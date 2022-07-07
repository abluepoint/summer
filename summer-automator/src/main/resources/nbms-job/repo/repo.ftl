package ${repoPackage};

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import ${entityClassName};

public interface ${repoClassSimpleName} extends JpaRepository<${entityClassSimpleName}, Long>, JpaSpecificationExecutor<${entityClassSimpleName}> {

}