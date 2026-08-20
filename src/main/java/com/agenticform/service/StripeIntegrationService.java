package com.agenticform.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import com.agenticform.dto.ConfirmPaymentRequest;
import com.agenticform.dto.ConfirmPaymentResponse;
import com.agenticform.dto.CreatePaymentIntentRequest;
import com.agenticform.dto.FormPageDto;
import com.agenticform.dto.PaymentConfigDto;
import com.agenticform.dto.StripePaymentIntentResponse;
import com.agenticform.dto.StripeStatusResponse;
import com.agenticform.exception.FormNotAvailableException;
import com.agenticform.exception.FormNotFoundException;
import com.agenticform.exception.StripeIntegrationException;
import com.agenticform.exception.StripeNotConfiguredException;
import com.agenticform.model.entity.Form;
import com.agenticform.model.entity.FormStatus;
import com.agenticform.model.entity.IntegrationConnection;
import com.agenticform.model.entity.User;
import com.agenticform.repository.FormRepository;
import com.agenticform.repository.IntegrationConnectionRepository;
import com.agenticform.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.Coupon;
import com.stripe.model.Customer;
import com.stripe.model.Invoice;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Price;
import com.stripe.model.Product;
import com.stripe.model.PromotionCode;
import com.stripe.model.SetupIntent;
import com.stripe.model.Subscription;
import com.stripe.param.CustomerCreateParams;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.PriceCreateParams;
import com.stripe.param.ProductCreateParams;
import com.stripe.param.PromotionCodeListParams;
import com.stripe.param.PaymentIntentUpdateParams;
import com.stripe.param.SetupIntentUpdateParams;
import com.stripe.param.SubscriptionCreateParams;

@Service
public class StripeIntegrationService {

    public static final String PROVIDER = "stripe";

    private static final String AUTHORIZE_URL = "https://connect.stripe.com/oauth/authorize";
    private static final String TOKEN_URL = "https://connect.stripe.com/oauth/token";
    private static final String API_BASE = "https://api.stripe.com/v1";

    private static final Set<String> ZERO_DECIMAL_CURRENCIES = Set.of(
            "bif", "clp", "djf", "gnf", "jpy", "kmf", "krw", "mga", "pyg", "rwf", "ugx",
            "vnd", "vuv", "xaf", "xof", "xpf");

    private final IntegrationConnectionRepository connectionRepository;
    private final UserRepository userRepository;
    private final FormRepository formRepository;
    private final FormMapper formMapper;
    private final EmailService emailService;
    private final RestClient restClient;
    private final String secretKey;
    private final String publishableKey;
    private final String clientId;
    private final String redirectUri;
    private final String frontendRedirectUri;
    private final String stateSecret;

    public StripeIntegrationService(
            IntegrationConnectionRepository connectionRepository,
            UserRepository userRepository,
            FormRepository formRepository,
            FormMapper formMapper,
            EmailService emailService,
            @Value("${app.stripe.secret-key:}") String secretKey,
            @Value("${app.stripe.publishable-key:}") String publishableKey,
            @Value("${app.stripe.client-id:}") String clientId,
            @Value("${app.stripe.redirect-uri:http://localhost:5173/api/v1/integrations/stripe/callback}")
                    String redirectUri,
            @Value("${app.oauth2.frontend-redirect-uri:http://localhost:5173/oauth2/redirect}")
                    String frontendRedirectUri,
            @Value("${jwt.secret}") String jwtSecret) {
        this.connectionRepository = connectionRepository;
        this.userRepository = userRepository;
        this.formRepository = formRepository;
        this.formMapper = formMapper;
        this.emailService = emailService;
        this.restClient = RestClient.create();
        this.secretKey = secretKey == null ? "" : secretKey.trim();
        this.publishableKey = publishableKey == null ? "" : publishableKey.trim();
        this.clientId = clientId == null ? "" : clientId.trim();
        this.redirectUri = redirectUri.trim();
        this.frontendRedirectUri = frontendRedirectUri.replace("/oauth2/redirect", "");
        this.stateSecret = jwtSecret;
        if (StringUtils.hasText(this.secretKey)) {
            Stripe.apiKey = this.secretKey;
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(secretKey) && StringUtils.hasText(publishableKey);
    }

    public boolean isConnectEnabled() {
        return StringUtils.hasText(clientId);
    }

    public StripeStatusResponse status(Long userId) {
        if (!isConfigured()) {
            return new StripeStatusResponse(false, false, false, null, null, null);
        }
        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(userId, PROVIDER)
                .orElse(null);
        boolean connected = connection != null || !isConnectEnabled();
        String email = connection == null ? null : connection.getProviderEmail();
        String accountId = connection == null ? null : connection.getOwnerUri();
        return new StripeStatusResponse(
                true,
                connected,
                isConnectEnabled(),
                email,
                publishableKey,
                accountId);
    }

    public String buildAuthorizeUrl(Long userId) {
        if (!isConfigured()) {
            throw new StripeNotConfiguredException();
        }
        if (!isConnectEnabled()) {
            throw new StripeIntegrationException(
                    "Stripe Connect n’est pas activé. Ajoutez STRIPE_CLIENT_ID ou utilisez le mode plateforme.");
        }
        String state = signState(userId);
        return UriComponentsBuilder.fromUriString(AUTHORIZE_URL)
                .queryParam("response_type", "code")
                .queryParam("client_id", clientId)
                .queryParam("scope", "read_write")
                .queryParam("redirect_uri", redirectUri)
                .queryParam("state", state)
                .encode()
                .build()
                .toUriString();
    }

    public String frontendCallbackUrl(boolean ok, String errorCode) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(frontendRedirectUri + "/integrations/stripe/callback");
        if (ok) {
            builder.queryParam("ok", "1");
        } else {
            builder.queryParam("ok", "0");
            if (StringUtils.hasText(errorCode)) {
                builder.queryParam("error", errorCode);
            }
        }
        return builder.encode().build().toUriString();
    }

    @Transactional
    public void handleCallback(String code, String state) {
        if (!isConfigured() || !isConnectEnabled()) {
            throw new StripeNotConfiguredException();
        }
        if (!StringUtils.hasText(code) || !StringUtils.hasText(state)) {
            throw new StripeIntegrationException("Autorisation Stripe incomplète.");
        }
        long userId = parseState(state);
        JsonNode token = exchangeConnectToken(code);
        String accessToken = text(token, "access_token");
        String refreshToken = text(token, "refresh_token");
        String stripeUserId = text(token, "stripe_user_id");
        if (!StringUtils.hasText(accessToken) || !StringUtils.hasText(stripeUserId)) {
            throw new StripeIntegrationException("Stripe n’a pas renvoyé de compte connecté.");
        }

        String email = fetchAccountEmail(stripeUserId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new StripeIntegrationException("Utilisateur introuvable."));

        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(userId, PROVIDER)
                .orElseGet(IntegrationConnection::new);
        connection.setUser(user);
        connection.setProvider(PROVIDER);
        connection.setAccessToken(accessToken);
        connection.setRefreshToken(refreshToken);
        connection.setExpiresAt(null);
        connection.setOwnerUri(stripeUserId);
        connection.setOrganizationUri(null);
        connection.setProviderEmail(email);
        connectionRepository.save(connection);
    }

    @Transactional
    public void disconnect(Long userId) {
        connectionRepository.deleteByUserIdAndProvider(userId, PROVIDER);
    }

    @Transactional(readOnly = true)
    public StripePaymentIntentResponse createPublicPaymentIntent(
            Long formId,
            CreatePaymentIntentRequest request) {
        if (!isConfigured()) {
            throw new StripeNotConfiguredException();
        }
        Form form = formRepository.findByIdWithFields(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new FormNotAvailableException(formId);
        }

        Long ownerId = ownerUserIdForForm(form);
        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(ownerId, PROVIDER)
                .orElse(null);
        if (isConnectEnabled() && connection == null) {
            throw new StripeIntegrationException(
                    "Le propriétaire du formulaire n’a pas encore connecté Stripe.");
        }
        String connectedAccountId = connection == null ? null : connection.getOwnerUri();

        FormPageDto paymentPage = resolvePaymentPage(form, request == null ? null : request.pageId());
        PaymentConfigDto config = paymentPage.paymentConfig();
        if (config == null) {
            throw new StripeIntegrationException("Configuration de paiement introuvable.");
        }

        String paymentType = config.paymentType() == null
                ? "one_time"
                : config.paymentType().trim().toLowerCase(Locale.ROOT);

        if ("subscription".equals(paymentType)) {
            return createSubscriptionCheckout(formId, paymentPage, config, request, connectedAccountId);
        }

        String currency = normalizeCurrency(config.currency());
        BigDecimal majorAmount = resolveChargeAmount(paymentType, config, request);
        long stripeAmount = toStripeAmount(majorAmount, currency);
        if (stripeAmount < 1) {
            throw new StripeIntegrationException("Montant de paiement invalide.");
        }

        String description = StringUtils.hasText(config.stripePaymentDescription())
                ? config.stripePaymentDescription().trim()
                : "Form payment";
        String productName = StringUtils.hasText(config.productName())
                ? config.productName().trim()
                : "Payment";

        try {
            com.stripe.net.RequestOptions options = StringUtils.hasText(connectedAccountId)
                    ? com.stripe.net.RequestOptions.builder()
                            .setStripeAccount(connectedAccountId)
                            .build()
                    : null;

            AppliedDiscount discount = resolveAppliedDiscount(config, request, stripeAmount, currency, options);
            long chargeAmount = discount == null ? stripeAmount : discount.finalAmount();
            if (chargeAmount < 1) {
                throw new StripeIntegrationException(
                        "Ce code promo réduit le montant à 0. Utilisez un autre code ou contactez le vendeur.");
            }

            PaymentIntentCreateParams.Builder params = PaymentIntentCreateParams.builder()
                    .setAmount(chargeAmount)
                    .setCurrency(currency)
                    .setDescription(description)
                    .putMetadata("formId", String.valueOf(formId))
                    .putMetadata("pageId", paymentPage.id() == null ? "" : paymentPage.id())
                    .putMetadata("productName", productName)
                    .putMetadata("paymentType", paymentType)
                    .putMetadata("originalAmount", String.valueOf(stripeAmount));

            if (discount != null) {
                params.putMetadata("discountCode", discount.code());
                params.putMetadata("discountLabel", discount.label());
                params.putMetadata("promotionCodeId", discount.promotionCodeId());
            }

            List<String> methodTypes = resolvePaymentMethodTypes(config);
            if (!methodTypes.isEmpty()) {
                for (String method : methodTypes) {
                    params.addPaymentMethodType(method);
                }
            } else {
                params.setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build());
            }

            if (request != null && StringUtils.hasText(request.email())) {
                params.setReceiptEmail(request.email().trim());
            }

            PaymentIntent intent = options == null
                    ? PaymentIntent.create(params.build())
                    : PaymentIntent.create(params.build(), options);

            return new StripePaymentIntentResponse(
                    intent.getClientSecret(),
                    publishableKey,
                    intent.getId(),
                    connectedAccountId,
                    chargeAmount,
                    currency,
                    "payment",
                    null,
                    discount == null ? null : stripeAmount,
                    discount == null ? null : discount.label());
        } catch (StripeException ex) {
            throw new StripeIntegrationException(
                    "Impossible de créer le paiement Stripe : " + ex.getMessage(), ex);
        }
    }

    private StripePaymentIntentResponse createSubscriptionCheckout(
            Long formId,
            FormPageDto paymentPage,
            PaymentConfigDto config,
            CreatePaymentIntentRequest request,
            String connectedAccountId) {
        if (request == null || !StringUtils.hasText(request.email())) {
            throw new StripeIntegrationException("Indiquez votre e-mail pour vous abonner.");
        }
        String currency = normalizeCurrency(config.currency());
        BigDecimal majorAmount = resolveChargeAmount("subscription", config, request);
        long stripeAmount = toStripeAmount(majorAmount, currency);
        if (stripeAmount < 1) {
            throw new StripeIntegrationException("Montant d’abonnement invalide.");
        }

        String productName = StringUtils.hasText(config.productName())
                ? config.productName().trim()
                : "Abonnement";
        String description = StringUtils.hasText(config.stripePaymentDescription())
                ? config.stripePaymentDescription().trim()
                : productName;
        String email = request.email().trim();
        int trialDays = resolveFreeTrialDays(config);
        PriceCreateParams.Recurring.Interval interval = resolveBillingInterval(config.billingInterval());

        try {
            com.stripe.net.RequestOptions options = StringUtils.hasText(connectedAccountId)
                    ? com.stripe.net.RequestOptions.builder()
                            .setStripeAccount(connectedAccountId)
                            .build()
                    : null;

            CustomerCreateParams.Builder customerParams = CustomerCreateParams.builder()
                    .setEmail(email)
                    .putMetadata("formId", String.valueOf(formId))
                    .putMetadata("pageId", paymentPage.id() == null ? "" : paymentPage.id());
            Customer customer = options == null
                    ? Customer.create(customerParams.build())
                    : Customer.create(customerParams.build(), options);

            ProductCreateParams.Builder productParams = ProductCreateParams.builder()
                    .setName(productName)
                    .setDescription(description)
                    .putMetadata("formId", String.valueOf(formId));
            Product product = options == null
                    ? Product.create(productParams.build())
                    : Product.create(productParams.build(), options);

            PriceCreateParams.Builder priceParams = PriceCreateParams.builder()
                    .setCurrency(currency)
                    .setUnitAmount(stripeAmount)
                    .setProduct(product.getId())
                    .setRecurring(PriceCreateParams.Recurring.builder()
                            .setInterval(interval)
                            .build());
            Price price = options == null
                    ? Price.create(priceParams.build())
                    : Price.create(priceParams.build(), options);

            AppliedDiscount discount = resolveAppliedDiscount(config, request, stripeAmount, currency, options);

            SubscriptionCreateParams.PaymentSettings.Builder paymentSettings =
                    SubscriptionCreateParams.PaymentSettings.builder()
                            .setSaveDefaultPaymentMethod(
                                    SubscriptionCreateParams.PaymentSettings.SaveDefaultPaymentMethod.ON_SUBSCRIPTION);
            for (String method : resolveSubscriptionPaymentMethodTypes(config)) {
                SubscriptionCreateParams.PaymentSettings.PaymentMethodType typed = switch (method) {
                    case "link" -> SubscriptionCreateParams.PaymentSettings.PaymentMethodType.LINK;
                    case "sepa_debit" -> SubscriptionCreateParams.PaymentSettings.PaymentMethodType.SEPA_DEBIT;
                    case "us_bank_account" ->
                            SubscriptionCreateParams.PaymentSettings.PaymentMethodType.US_BANK_ACCOUNT;
                    default -> SubscriptionCreateParams.PaymentSettings.PaymentMethodType.CARD;
                };
                paymentSettings.addPaymentMethodType(typed);
            }

            SubscriptionCreateParams.Builder subParams = SubscriptionCreateParams.builder()
                    .setCustomer(customer.getId())
                    .addItem(SubscriptionCreateParams.Item.builder()
                            .setPrice(price.getId())
                            .build())
                    .setPaymentBehavior(SubscriptionCreateParams.PaymentBehavior.DEFAULT_INCOMPLETE)
                    .setPaymentSettings(paymentSettings.build())
                    .putMetadata("formId", String.valueOf(formId))
                    .putMetadata("pageId", paymentPage.id() == null ? "" : paymentPage.id())
                    .putMetadata("productName", productName)
                    .putMetadata("paymentType", "subscription")
                    .putMetadata("customerEmail", email)
                    .putMetadata("originalAmount", String.valueOf(stripeAmount))
                    .addExpand("latest_invoice.payment_intent")
                    .addExpand("pending_setup_intent");

            if (discount != null) {
                subParams.addDiscount(SubscriptionCreateParams.Discount.builder()
                        .setPromotionCode(discount.promotionCodeId())
                        .build());
                subParams.putMetadata("discountCode", discount.code());
                subParams.putMetadata("discountLabel", discount.label());
                subParams.putMetadata("promotionCodeId", discount.promotionCodeId());
            }

            if (trialDays > 0) {
                subParams.setTrialPeriodDays((long) trialDays);
            }

            Subscription subscription = options == null
                    ? Subscription.create(subParams.build())
                    : Subscription.create(subParams.build(), options);

            PaymentIntent paymentIntent = null;
            Invoice latestInvoice = subscription.getLatestInvoiceObject();
            if (latestInvoice != null) {
                paymentIntent = latestInvoice.getPaymentIntentObject();
            }
            SetupIntent setupIntent = subscription.getPendingSetupIntentObject();

            long chargeAmount = paymentIntent != null && paymentIntent.getAmount() != null
                    ? paymentIntent.getAmount()
                    : (discount == null ? stripeAmount : discount.finalAmount());
            Long originalAmount = discount == null ? null : stripeAmount;
            String discountLabel = discount == null ? null : discount.label();

            if (paymentIntent != null && StringUtils.hasText(paymentIntent.getClientSecret())) {
                if (discount != null) {
                    attachDiscountMetadata(paymentIntent.getId(), false, discount, stripeAmount,
                            formId, paymentPage, productName, email, options);
                }
                return new StripePaymentIntentResponse(
                        paymentIntent.getClientSecret(),
                        publishableKey,
                        paymentIntent.getId(),
                        connectedAccountId,
                        chargeAmount,
                        currency,
                        "payment",
                        subscription.getId(),
                        originalAmount,
                        discountLabel);
            }
            if (setupIntent != null && StringUtils.hasText(setupIntent.getClientSecret())) {
                if (discount != null) {
                    attachDiscountMetadata(setupIntent.getId(), true, discount, stripeAmount,
                            formId, paymentPage, productName, email, options);
                }
                return new StripePaymentIntentResponse(
                        setupIntent.getClientSecret(),
                        publishableKey,
                        setupIntent.getId(),
                        connectedAccountId,
                        chargeAmount,
                        currency,
                        "setup",
                        subscription.getId(),
                        originalAmount,
                        discountLabel);
            }
            throw new StripeIntegrationException(
                    "Impossible de démarrer l’abonnement Stripe (secret manquant).");
        } catch (StripeException ex) {
            throw new StripeIntegrationException(
                    "Impossible de créer l’abonnement Stripe : " + ex.getMessage(), ex);
        } catch (IllegalArgumentException ex) {
            throw new StripeIntegrationException(
                    "Méthode de paiement non supportée pour les abonnements.", ex);
        }
    }

    private record AppliedDiscount(
            String promotionCodeId,
            String code,
            String label,
            long finalAmount) {
    }

    private AppliedDiscount resolveAppliedDiscount(
            PaymentConfigDto config,
            CreatePaymentIntentRequest request,
            long stripeAmount,
            String currency,
            com.stripe.net.RequestOptions options) throws StripeException {
        boolean codeProvided = request != null && StringUtils.hasText(request.discountCode());
        if (!Boolean.TRUE.equals(config.allowDiscountCodes())) {
            if (codeProvided) {
                throw new StripeIntegrationException("Les codes promo ne sont pas activés sur ce paiement.");
            }
            return null;
        }
        if (!codeProvided) {
            return null;
        }
        String rawCode = request.discountCode().trim();
        // Stripe compare souvent en majuscules ; on normalise pour éviter les ratés.
        String lookupCode = rawCode.toUpperCase(Locale.ROOT);
        PromotionCodeListParams listParams = PromotionCodeListParams.builder()
                .setCode(lookupCode)
                .setActive(true)
                .setLimit(1L)
                .addExpand("data.coupon")
                .build();
        var listed = options == null
                ? PromotionCode.list(listParams)
                : PromotionCode.list(listParams, options);
        if ((listed.getData() == null || listed.getData().isEmpty()) && !lookupCode.equals(rawCode)) {
            listParams = PromotionCodeListParams.builder()
                    .setCode(rawCode)
                    .setActive(true)
                    .setLimit(1L)
                    .addExpand("data.coupon")
                    .build();
            listed = options == null
                    ? PromotionCode.list(listParams)
                    : PromotionCode.list(listParams, options);
        }
        if (listed.getData() == null || listed.getData().isEmpty()) {
            throw new StripeIntegrationException(
                    "Code promo invalide ou expiré. Vérifiez qu’il existe en mode test Stripe (même compte que vos clés sk_test).");
        }
        PromotionCode promotionCode = listed.getData().get(0);
        Coupon coupon = promotionCode.getCoupon();
        if (coupon == null) {
            throw new StripeIntegrationException("Code promo invalide ou expiré.");
        }
        // Parfois seul l’id est présent (sans expand) — récupérer le coupon complet.
        if (coupon.getPercentOff() == null && coupon.getAmountOff() == null && StringUtils.hasText(coupon.getId())) {
            coupon = options == null
                    ? Coupon.retrieve(coupon.getId())
                    : Coupon.retrieve(coupon.getId(), options);
        }
        if (coupon == null || Boolean.FALSE.equals(coupon.getValid())) {
            throw new StripeIntegrationException("Code promo invalide ou expiré.");
        }
        long finalAmount = applyCouponToAmount(stripeAmount, coupon, currency);
        String label = formatCouponLabel(coupon, currency);
        String code = StringUtils.hasText(promotionCode.getCode()) ? promotionCode.getCode() : rawCode;
        return new AppliedDiscount(promotionCode.getId(), code, label, finalAmount);
    }

    private static long applyCouponToAmount(long stripeAmount, Coupon coupon, String currency) {
        if (coupon.getPercentOff() != null) {
            BigDecimal percent = coupon.getPercentOff();
            BigDecimal remaining = BigDecimal.valueOf(100).subtract(percent);
            return BigDecimal.valueOf(stripeAmount)
                    .multiply(remaining)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValue();
        }
        if (coupon.getAmountOff() != null) {
            String couponCurrency = coupon.getCurrency() == null
                    ? currency
                    : coupon.getCurrency().trim().toLowerCase(Locale.ROOT);
            if (!currency.equals(couponCurrency)) {
                throw new StripeIntegrationException(
                        "Ce code promo n’est pas valable pour cette devise.");
            }
            return Math.max(0L, stripeAmount - coupon.getAmountOff());
        }
        throw new StripeIntegrationException("Code promo non applicable.");
    }

    private static String formatCouponLabel(Coupon coupon, String currency) {
        if (coupon.getPercentOff() != null) {
            String percent = coupon.getPercentOff().stripTrailingZeros().toPlainString().replace('.', ',');
            return "-" + percent + " %";
        }
        if (coupon.getAmountOff() != null) {
            return "-" + formatStripeMoney(coupon.getAmountOff(), currency);
        }
        return "réduction";
    }

    private static int resolveFreeTrialDays(PaymentConfigDto config) {
        if (config == null || !Boolean.TRUE.equals(config.includeFreeTrial())) {
            return 0;
        }
        if (!StringUtils.hasText(config.freeTrialDays())) {
            return 14;
        }
        try {
            int days = Integer.parseInt(config.freeTrialDays().trim());
            return Math.max(0, Math.min(days, 730));
        } catch (NumberFormatException ex) {
            return 14;
        }
    }

    private static PriceCreateParams.Recurring.Interval resolveBillingInterval(String raw) {
        String value = StringUtils.hasText(raw) ? raw.trim().toLowerCase(Locale.ROOT) : "month";
        return switch (value) {
            case "day" -> PriceCreateParams.Recurring.Interval.DAY;
            case "week" -> PriceCreateParams.Recurring.Interval.WEEK;
            case "year" -> PriceCreateParams.Recurring.Interval.YEAR;
            default -> PriceCreateParams.Recurring.Interval.MONTH;
        };
    }

    private static List<String> resolveSubscriptionPaymentMethodTypes(PaymentConfigDto config) {
        List<String> methods = resolvePaymentMethodTypes(config);
        if (methods.isEmpty()) {
            return List.of("card");
        }
        List<String> allowed = new ArrayList<>();
        for (String method : methods) {
            if ("card".equals(method)
                    || "link".equals(method)
                    || "sepa_debit".equals(method)
                    || "us_bank_account".equals(method)) {
                allowed.add(method);
            }
        }
        return allowed.isEmpty() ? List.of("card") : allowed;
    }

    @Transactional(readOnly = true)
    public ConfirmPaymentResponse confirmPublicPayment(Long formId, ConfirmPaymentRequest request) {
        if (!isConfigured()) {
            throw new StripeNotConfiguredException();
        }
        if (request == null || !StringUtils.hasText(request.paymentIntentId())) {
            throw new StripeIntegrationException("Identifiant de paiement manquant.");
        }
        if ("preview_payment".equals(request.paymentIntentId())) {
            return new ConfirmPaymentResponse(true, request.paymentIntentId());
        }

        Form form = formRepository.findByIdWithFields(formId)
                .orElseThrow(() -> new FormNotFoundException(formId));
        if (form.getStatus() != FormStatus.PUBLISHED) {
            throw new FormNotAvailableException(formId);
        }

        Long ownerId = ownerUserIdForForm(form);
        IntegrationConnection connection = connectionRepository
                .findByUserIdAndProvider(ownerId, PROVIDER)
                .orElse(null);
        String connectedAccountId = connection == null ? null : connection.getOwnerUri();

        FormPageDto paymentPage = resolvePaymentPage(form, request.pageId());
        PaymentConfigDto config = paymentPage.paymentConfig();
        if (config == null) {
            throw new StripeIntegrationException("Configuration de paiement introuvable.");
        }

        String intentId = request.paymentIntentId().trim();
        com.stripe.net.RequestOptions options = StringUtils.hasText(connectedAccountId)
                ? com.stripe.net.RequestOptions.builder()
                        .setStripeAccount(connectedAccountId)
                        .build()
                : null;

        String productName;
        String amountLabel;
        String customerEmail;
        String metaFormId;

        if (intentId.startsWith("seti_")) {
            SetupIntent setupIntent;
            try {
                setupIntent = options == null
                        ? SetupIntent.retrieve(intentId)
                        : SetupIntent.retrieve(intentId, options);
            } catch (StripeException ex) {
                throw new StripeIntegrationException(
                        "Impossible de vérifier l’abonnement Stripe : " + ex.getMessage(), ex);
            }
            String status = setupIntent.getStatus();
            if (!"succeeded".equals(status)) {
                throw new StripeIntegrationException("L’abonnement n’est pas encore confirmé.");
            }
            metaFormId = setupIntent.getMetadata() != null ? setupIntent.getMetadata().get("formId") : null;
            productName = setupIntent.getMetadata() != null
                    ? setupIntent.getMetadata().get("productName")
                    : null;
            customerEmail = firstNonBlank(
                    request.email(),
                    setupIntent.getMetadata() != null ? setupIntent.getMetadata().get("customerEmail") : null);
            amountLabel = buildEmailAmountLabel(
                    config,
                    setupIntent.getMetadata(),
                    null,
                    normalizeCurrency(config.currency()),
                    true);
            amountLabel = prependTrialEmailLines(config, amountLabel);
        } else {
            PaymentIntent intent;
            try {
                intent = options == null
                        ? PaymentIntent.retrieve(intentId)
                        : PaymentIntent.retrieve(intentId, options);
            } catch (StripeException ex) {
                throw new StripeIntegrationException(
                        "Impossible de vérifier le paiement Stripe : " + ex.getMessage(), ex);
            }

            metaFormId = intent.getMetadata() != null ? intent.getMetadata().get("formId") : null;
            String status = intent.getStatus();
            if (!"succeeded".equals(status) && !"processing".equals(status)) {
                throw new StripeIntegrationException("Le paiement n’est pas encore confirmé.");
            }

            productName = intent.getMetadata() != null
                    ? intent.getMetadata().get("productName")
                    : null;
            boolean isSubscriptionPayment = "subscription".equalsIgnoreCase(
                    config.paymentType() == null ? "" : config.paymentType().trim());
            if (!isSubscriptionPayment && intent.getMetadata() != null) {
                isSubscriptionPayment = "subscription".equalsIgnoreCase(
                        intent.getMetadata().get("paymentType"));
            }
            amountLabel = buildEmailAmountLabel(
                    config,
                    intent.getMetadata(),
                    intent.getAmount(),
                    intent.getCurrency() != null ? intent.getCurrency() : normalizeCurrency(config.currency()),
                    isSubscriptionPayment);
            if (isSubscriptionPayment) {
                amountLabel = prependTrialEmailLines(config, amountLabel);
            }
            customerEmail = firstNonBlank(
                    request.email(),
                    intent.getReceiptEmail(),
                    intent.getMetadata() != null ? intent.getMetadata().get("customerEmail") : null);
        }

        if (StringUtils.hasText(metaFormId) && !String.valueOf(formId).equals(metaFormId)) {
            throw new StripeIntegrationException("Ce paiement ne correspond pas à ce formulaire.");
        }

        if (!StringUtils.hasText(productName) && StringUtils.hasText(config.productName())) {
            productName = config.productName().trim();
        }
        String formTitle = StringUtils.hasText(form.getTitle()) ? form.getTitle().trim() : "Formulaire";

        boolean sendReceipt = !Boolean.FALSE.equals(config.sendReceipt());
        if (sendReceipt && StringUtils.hasText(customerEmail)) {
            emailService.sendPaymentConfirmationToCustomer(
                    customerEmail,
                    null,
                    productName,
                    amountLabel,
                    formTitle);
        }

        User owner = userRepository.findById(ownerId).orElse(null);
        if (owner != null && StringUtils.hasText(owner.getEmail())) {
            emailService.sendPaymentNotificationToOwner(
                    owner.getEmail(),
                    owner.getFullName(),
                    productName,
                    amountLabel,
                    customerEmail,
                    formTitle);
        }

        return new ConfirmPaymentResponse(true, intentId);
    }

    private static String formatSubscriptionAmountLabel(PaymentConfigDto config) {
        if (config == null || config.amount() == null) {
            return "—";
        }
        String money = formatStripeMoney(
                toStripeAmount(config.amount(), normalizeCurrency(config.currency())),
                normalizeCurrency(config.currency()));
        return money + subscriptionSuffix(config);
    }

    private static String subscriptionSuffix(PaymentConfigDto config) {
        String interval = config != null && StringUtils.hasText(config.billingInterval())
                ? config.billingInterval().trim().toLowerCase(Locale.ROOT)
                : "month";
        return switch (interval) {
            case "day" -> "/jour";
            case "week" -> "/semaine";
            case "year" -> "/an";
            default -> "/mois";
        };
    }

    private static String buildEmailAmountLabel(
            PaymentConfigDto config,
            java.util.Map<String, String> metadata,
            Long chargedAmountMinor,
            String currency,
            boolean subscription) {
        String cur = normalizeCurrency(currency != null ? currency : (config != null ? config.currency() : "eur"));
        String discountCode = metadata != null ? metadata.get("discountCode") : null;
        String discountLabel = metadata != null ? metadata.get("discountLabel") : null;
        String originalRaw = metadata != null ? metadata.get("originalAmount") : null;

        boolean hasDiscount = StringUtils.hasText(discountCode) || StringUtils.hasText(discountLabel);
        if (!hasDiscount) {
            if (subscription) {
                return formatSubscriptionAmountLabel(config);
            }
            if (chargedAmountMinor != null) {
                return formatStripeMoney(chargedAmountMinor, cur);
            }
            return formatSubscriptionAmountLabel(config);
        }

        long originalMinor;
        if (StringUtils.hasText(originalRaw)) {
            try {
                originalMinor = Long.parseLong(originalRaw.trim());
            } catch (NumberFormatException ex) {
                originalMinor = config != null && config.amount() != null
                        ? toStripeAmount(config.amount(), cur)
                        : (chargedAmountMinor != null ? chargedAmountMinor : 0L);
            }
        } else if (config != null && config.amount() != null) {
            originalMinor = toStripeAmount(config.amount(), cur);
        } else {
            originalMinor = chargedAmountMinor != null ? chargedAmountMinor : 0L;
        }

        long finalMinor = chargedAmountMinor != null ? chargedAmountMinor : originalMinor;
        if (chargedAmountMinor == null && StringUtils.hasText(discountLabel) && discountLabel.contains("%")) {
            try {
                String digits = discountLabel.replaceAll("[^0-9,.-]", "").replace(',', '.');
                if (digits.startsWith("-")) {
                    digits = digits.substring(1);
                }
                BigDecimal percent = new BigDecimal(digits);
                finalMinor = BigDecimal.valueOf(originalMinor)
                        .multiply(BigDecimal.valueOf(100).subtract(percent))
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        .longValue();
            } catch (RuntimeException ignored) {
                finalMinor = originalMinor;
            }
        }

        String suffix = subscription ? subscriptionSuffix(config) : "";
        String originalLabel = formatStripeMoney(originalMinor, cur) + suffix;
        String finalLabel = formatStripeMoney(finalMinor, cur) + suffix;

        StringBuilder promo = new StringBuilder("Grâce à la réduction");
        if (StringUtils.hasText(discountCode) || StringUtils.hasText(discountLabel)) {
            promo.append(" (");
            if (StringUtils.hasText(discountCode)) {
                promo.append(discountCode.trim());
            }
            if (StringUtils.hasText(discountLabel)) {
                if (StringUtils.hasText(discountCode)) {
                    promo.append(", ");
                }
                promo.append(discountLabel.trim());
            }
            promo.append(")");
        }
        promo.append(" : ").append(finalLabel);

        return "Prix : " + originalLabel + "\n" + promo;
    }

    private static String prependTrialEmailLines(PaymentConfigDto config, String amountLabel) {
        if (config == null || !Boolean.TRUE.equals(config.includeFreeTrial())) {
            return amountLabel;
        }
        int days = resolveFreeTrialDays(config);
        String dueToday = formatStripeMoney(0L, normalizeCurrency(config.currency())) + " dû aujourd'hui";
        String header = "Essai gratuit : " + days + " jour" + (days > 1 ? "s" : "")
                + "\n" + dueToday + "\nPuis :";
        if (!StringUtils.hasText(amountLabel)) {
            return header;
        }
        return header + "\n" + amountLabel.trim();
    }

    private void attachDiscountMetadata(
            String intentId,
            boolean setupIntent,
            AppliedDiscount discount,
            long originalAmount,
            Long formId,
            FormPageDto paymentPage,
            String productName,
            String email,
            com.stripe.net.RequestOptions options) {
        try {
            if (setupIntent) {
                SetupIntentUpdateParams params = SetupIntentUpdateParams.builder()
                        .putMetadata("formId", String.valueOf(formId))
                        .putMetadata("pageId", paymentPage.id() == null ? "" : paymentPage.id())
                        .putMetadata("productName", productName)
                        .putMetadata("paymentType", "subscription")
                        .putMetadata("customerEmail", email)
                        .putMetadata("originalAmount", String.valueOf(originalAmount))
                        .putMetadata("discountCode", discount.code())
                        .putMetadata("discountLabel", discount.label())
                        .putMetadata("promotionCodeId", discount.promotionCodeId())
                        .build();
                SetupIntent existing = options == null
                        ? SetupIntent.retrieve(intentId)
                        : SetupIntent.retrieve(intentId, options);
                if (options == null) {
                    existing.update(params);
                } else {
                    existing.update(params, options);
                }
            } else {
                PaymentIntentUpdateParams params = PaymentIntentUpdateParams.builder()
                        .putMetadata("formId", String.valueOf(formId))
                        .putMetadata("pageId", paymentPage.id() == null ? "" : paymentPage.id())
                        .putMetadata("productName", productName)
                        .putMetadata("paymentType", "subscription")
                        .putMetadata("customerEmail", email)
                        .putMetadata("originalAmount", String.valueOf(originalAmount))
                        .putMetadata("discountCode", discount.code())
                        .putMetadata("discountLabel", discount.label())
                        .putMetadata("promotionCodeId", discount.promotionCodeId())
                        .build();
                PaymentIntent existing = options == null
                        ? PaymentIntent.retrieve(intentId)
                        : PaymentIntent.retrieve(intentId, options);
                if (options == null) {
                    existing.update(params);
                } else {
                    existing.update(params, options);
                }
            }
        } catch (StripeException ignored) {
            // Les mails pourront quand même retomber sur le prix catalogue.
        }
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static String formatStripeMoney(Long amount, String currency) {
        if (amount == null) {
            return "—";
        }
        String cur = normalizeCurrency(currency);
        BigDecimal major = ZERO_DECIMAL_CURRENCIES.contains(cur)
                ? BigDecimal.valueOf(amount)
                : BigDecimal.valueOf(amount, 2);
        String number = major.setScale(
                        ZERO_DECIMAL_CURRENCIES.contains(cur) ? 0 : 2,
                        RoundingMode.HALF_UP)
                .toPlainString()
                .replace('.', ',');
        String symbol = switch (cur) {
            case "eur" -> "€";
            case "usd" -> "$";
            case "gbp" -> "£";
            default -> cur.toUpperCase(Locale.ROOT);
        };
        if ("eur".equals(cur) || "usd".equals(cur) || "gbp".equals(cur)) {
            return number + " " + symbol;
        }
        return number + " " + symbol;
    }

    private FormPageDto resolvePaymentPage(Form form, String pageId) {
        for (FormPageDto page : formMapper.parsePages(form.getPagesJson())) {
            if (page == null || !"payment".equalsIgnoreCase(page.type())) {
                continue;
            }
            if (!StringUtils.hasText(pageId) || pageId.equals(page.id())) {
                return page;
            }
        }
        throw new StripeIntegrationException("Page de paiement introuvable sur ce formulaire.");
    }

    private BigDecimal resolveChargeAmount(
            String paymentType,
            PaymentConfigDto config,
            CreatePaymentIntentRequest request) {
        if ("pay_what_you_want".equals(paymentType)) {
            BigDecimal chosen = request != null && request.amount() != null
                    ? request.amount()
                    : config.presetAmount();
            if (chosen == null) {
                throw new StripeIntegrationException("Indiquez un montant à payer.");
            }
            if (Boolean.TRUE.equals(config.defineLimits())) {
                if (config.minAmount() != null && chosen.compareTo(config.minAmount()) < 0) {
                    throw new StripeIntegrationException("Montant inférieur au minimum autorisé.");
                }
                if (config.maxAmount() != null && chosen.compareTo(config.maxAmount()) > 0) {
                    throw new StripeIntegrationException("Montant supérieur au maximum autorisé.");
                }
            }
            return chosen;
        }
        if (config.amount() == null || config.amount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new StripeIntegrationException("Le produit n’a pas de prix configuré.");
        }
        return config.amount();
    }

    private static List<String> resolvePaymentMethodTypes(PaymentConfigDto config) {
        if (config == null || config.paymentMethods() == null || config.paymentMethods().isEmpty()) {
            return List.of();
        }
        List<String> methods = new ArrayList<>();
        for (String raw : config.paymentMethods()) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String id = raw.trim().toLowerCase(Locale.ROOT);
            switch (id) {
                case "card", "link", "klarna", "bancontact", "sepa_debit", "us_bank_account", "affirm" -> {
                    if (!methods.contains(id)) {
                        methods.add(id);
                    }
                }
                default -> {
                    // ignore unknown
                }
            }
        }
        return methods;
    }

    private static String normalizeCurrency(String currency) {
        if (!StringUtils.hasText(currency)) {
            return "usd";
        }
        return currency.trim().toLowerCase(Locale.ROOT);
    }

    private static long toStripeAmount(BigDecimal amount, String currency) {
        BigDecimal safe = amount == null ? BigDecimal.ZERO : amount;
        if (ZERO_DECIMAL_CURRENCIES.contains(currency)) {
            return safe.setScale(0, RoundingMode.HALF_UP).longValueExact();
        }
        return safe.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    private Long ownerUserIdForForm(Form form) {
        if (form.getWorkspace() != null && form.getWorkspace().getOwner() != null) {
            return form.getWorkspace().getOwner().getId();
        }
        if (form.getCreatedBy() != null) {
            return form.getCreatedBy().getId();
        }
        throw new StripeIntegrationException("Organisateur du formulaire introuvable.");
    }

    private JsonNode exchangeConnectToken(String code) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", clientId);
        form.add("client_secret", secretKey);
        form.add("code", code);
        try {
            JsonNode body = restClient.post()
                    .uri(TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                throw new StripeIntegrationException("Réponse Stripe vide.");
            }
            if (body.hasNonNull("error")) {
                throw new StripeIntegrationException(
                        "Échec OAuth Stripe : " + body.path("error_description").asText("error"));
            }
            return body;
        } catch (RestClientException ex) {
            throw new StripeIntegrationException("Échec de la connexion à Stripe.", ex);
        }
    }

    private String fetchAccountEmail(String stripeAccountId) {
        try {
            JsonNode body = restClient.get()
                    .uri(API_BASE + "/accounts/" + stripeAccountId)
                    .header("Authorization", "Bearer " + secretKey)
                    .retrieve()
                    .body(JsonNode.class);
            if (body == null) {
                return null;
            }
            String email = text(body, "email");
            if (StringUtils.hasText(email)) {
                return email;
            }
            return text(body.path("business_profile"), "support_email");
        } catch (RestClientException ex) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String value = node.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private String signState(Long userId) {
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String payload = userId + "." + Instant.now().getEpochSecond() + "." + nonce;
        return Base64.getUrlEncoder().withoutPadding()
                .encodeToString((payload + "." + hmac(payload)).getBytes(StandardCharsets.UTF_8));
    }

    private long parseState(String state) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(state), StandardCharsets.UTF_8);
            String[] parts = decoded.split("\\.");
            if (parts.length != 4) {
                throw new StripeIntegrationException("État OAuth Stripe invalide.");
            }
            String payload = parts[0] + "." + parts[1] + "." + parts[2];
            if (!hmac(payload).equals(parts[3])) {
                throw new StripeIntegrationException("État OAuth Stripe invalide.");
            }
            long issued = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() - issued > 600) {
                throw new StripeIntegrationException("La connexion Stripe a expiré. Réessayez.");
            }
            return Long.parseLong(parts[0]);
        } catch (StripeIntegrationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new StripeIntegrationException("État OAuth Stripe invalide.");
        }
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(stateSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Impossible de signer l’état OAuth Stripe.", ex);
        }
    }
}
