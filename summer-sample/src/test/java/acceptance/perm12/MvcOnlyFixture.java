package acceptance.perm12;

/** Same explicit whole-realtime omission as the original ledger-only fixture. */
@org.springframework.context.annotation.Configuration(proxyBeanMethods = false)
class MvcOnlyFixture {
  @org.springframework.context.annotation.Bean
  static org.springframework.beans.factory.config.BeanFactoryPostProcessor omitRealtimeSubsystem() {
    return factory -> {
      var registry = (org.springframework.beans.factory.support.BeanDefinitionRegistry) factory;
      var removed = new java.util.HashSet<String>();
      boolean changed;
      do {
        changed = false;
        for (String name : registry.getBeanDefinitionNames()) {
          var definition = registry.getBeanDefinition(name);
          String owner = definition.getBeanClassName();
          if (owner != null && owner.startsWith("com.abluepoint.summer.sample.realtime.")
              || removed.contains(definition.getFactoryBeanName())) {
            registry.removeBeanDefinition(name);
            removed.add(name);
            changed = true;
          }
        }
      } while (changed);
    };
  }
}
