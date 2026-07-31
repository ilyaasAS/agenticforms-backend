package com.agenticform.service;

import com.agenticform.model.entity.User;

/** Résultat OAuth : utilisateur + si le compte vient d'être créé. */
public record OAuthUserResult(User user, boolean created) {
}
