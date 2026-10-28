package com.abluepoint.summer.sample.plugin.infrastructure;


import org.springframework.data.jpa.repository.JpaRepository;

public interface PluginRepository extends JpaRepository<PluginEntity, String> {}
