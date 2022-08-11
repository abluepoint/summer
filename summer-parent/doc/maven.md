# maven

## version

```shell
mvn versions:set -DgenerateBackupPoms=false -DnewVersion=3.2.4 && mvn -N versions:update-child-modules
```

## deploy

```shell
mvn clean deploy -Dmaven.test.skip
```