package acceptance.perm12;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.stream.*;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.*;
import org.junit.platform.launcher.core.*;

/** Standalone worker: no concurrent Gradle JVM; worker plus old child uses at most two slots. */
public final class HarnessJUnitMain {
  record Case(String className, String name, String status, String detail) {}
  public static void main(String[] args) throws Exception {
    RunSettings.properties();
    for (String gate : List.of("SUMMER_PERM12_MYSQL", "SUMMER_PERM13_MYSQL"))
      if (!"true".equals(System.getenv(gate))) throw new IllegalArgumentException("Both original native opt-in gates must be true");
    var cases = new ArrayList<Case>();
    String caseId = RunSettings.required("PERM12_HARNESS_CASE_ID");
    var request = LauncherDiscoveryRequestBuilder.request().selectors(selectMethod(caseId))
        .configurationParameter("junit.jupiter.execution.parallel.enabled", "false")
        .configurationParameter("junit.jupiter.testinstance.lifecycle.default", "per_method")
        .build();
    TestExecutionListener listener = new TestExecutionListener() {
      private Case row(TestIdentifier id, String status, String detail) {
        String className = "JUnitContainer", name = id.getDisplayName();
        if (id.getSource().orElse(null) instanceof MethodSource source) {
          className = source.getClassName(); name = source.getMethodName();
        }
        return new Case(className, name, status, RunSettings.redact(detail));
      }
      @Override public void executionSkipped(TestIdentifier id, String reason) {
        if (id.isTest()) cases.add(row(id, "skipped", reason));
      }
      @Override public void executionFinished(TestIdentifier id, TestExecutionResult result) {
        String status = switch (result.getStatus()) {
          case SUCCESSFUL -> "passed";
          case ABORTED -> "skipped";
          case FAILED -> "failure";
        };
        if (id.isTest() || result.getStatus() == TestExecutionResult.Status.FAILED) {
          var detail = new StringWriter();
          result.getThrowable().ifPresent(t -> t.printStackTrace(new PrintWriter(detail)));
          cases.add(row(id, status, detail.toString()));
        }
      }
    };
    var launcher = LauncherFactory.create();
    var discovered = launcher.discover(request);
    long discoveredTests = discovered.countTestIdentifiers(TestIdentifier::isTest);
    if (discoveredTests != 1) throw new AssertionError("Exactly the one leased original method must be discovered");
    launcher.execute(request, listener);
    long failures = cases.stream().filter(c -> c.status().equals("failure")).count();
    long skipped = cases.stream().filter(c -> c.status().equals("skipped")).count();
    var byClass = new LinkedHashMap<String, List<Case>>();
    for (var c : cases) byClass.computeIfAbsent(c.className(), ignored -> new ArrayList<>()).add(c);
    for (var entry : byClass.entrySet()) writeXml(entry.getKey(), entry.getValue());
    boolean complete = cases.size() == 1 && failures == 0 && skipped == 0
        && (cases.getFirst().className() + "#" + cases.getFirst().name()).equals(caseId);
    RunSettings.JSON.writeValue(RunSettings.work().resolve("case-result.json").toFile(), Map.of(
        "discovered", discoveredTests, "tests", cases.size(), "failures", failures,
        "skipped", skipped, "caseCompletedWithoutSkip", complete, "caseId", caseId,
        "scope", "one-original-method-per-owned-schema", "fullNativeSuiteCredit", false));
    System.out.println("Canonical migration protocol cases=" + cases.size() + " failures=" + failures + " skipped=" + skipped);
    System.exit(complete ? 0 : 1);
  }

  private static void writeXml(String className, List<Case> cases) throws Exception {
    long failures = cases.stream().filter(c -> c.status().equals("failure")).count();
    long skipped = cases.stream().filter(c -> c.status().equals("skipped")).count();
    Path report = RunSettings.work().resolve("TEST-" + className + ".xml");
    try (var output = Files.newOutputStream(report, StandardOpenOption.CREATE_NEW)) {
      var xml = XMLOutputFactory.newFactory().createXMLStreamWriter(output, "UTF-8");
      xml.writeStartDocument("UTF-8", "1.0"); xml.writeStartElement("testsuite");
      xml.writeAttribute("name", className);
      xml.writeAttribute("tests", Integer.toString(cases.size()));
      xml.writeAttribute("failures", Long.toString(failures));
      xml.writeAttribute("errors", "0"); xml.writeAttribute("skipped", Long.toString(skipped));
      for (var c : cases) {
        xml.writeStartElement("testcase"); xml.writeAttribute("classname", c.className());
        xml.writeAttribute("name", c.name());
        if (!c.status().equals("passed")) {
          xml.writeStartElement(c.status().equals("skipped") ? "skipped" : "failure");
          xml.writeCharacters(c.detail()); xml.writeEndElement();
        }
        xml.writeEndElement();
      }
      xml.writeEndElement(); xml.writeEndDocument(); xml.close();
    }
  }
}
