package com.abluepoint.summer.sample.key;

import org.springframework.data.jpa.repository.JpaRepository;

interface KeyRotationStateRepository extends JpaRepository<KeyRotationStateEntity, Integer> {}
