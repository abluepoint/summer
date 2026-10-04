package com.abluepoint.summer.sample.plugin;

import org.springframework.data.jpa.repository.JpaRepository;

interface PluginRepository extends JpaRepository<PluginEntity, String> {}
