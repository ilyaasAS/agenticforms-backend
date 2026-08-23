package com.agenticform.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.agenticform.model.document.ContactMessage;

public interface ContactMessageRepository extends MongoRepository<ContactMessage, String> {

    List<ContactMessage> findAllByOrderByCreatedAtDesc();
}
