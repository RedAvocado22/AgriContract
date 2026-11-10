"""Report 5 section I (Overall Plan): replaces the template's blue guidance text and fills its tables.

Run once on the template version of Report-5.0 (the blue placeholders must still be there).
Body paragraphs are written in the document's normal body format (the placeholder run minus italic/blue),
lists reuse the template's own bullet list, tables keep their header rows and row formatting.
"""
import copy
import sys
from pathlib import Path

import docx
from docx.oxml.ns import qn

sys.path.insert(0, str(Path(__file__).parent))
from docxedit import fill_change_log, find, next_table, set_par_text, set_table_rows, text_of  # noqa: E402

DOC = Path(__file__).resolve().parents[1] / "Report-5.0_Test Documentation.docx"
d = docx.Document(DOC)


def plain(p):
    """Drops the guidance look (italic, blue) from the paragraph's runs."""
    for rpr in p.iter(qn("w:rPr")):
        for tag in ("w:i", "w:iCs", "w:color"):
            for el in rpr.findall(qn(tag)):
                rpr.remove(el)
    return p


BULLET_SRC = find(d, "Unit Test Cases:")  # template bullet list paragraph (ListParagraph, bullet numbering)


def write_block(anchor_text, items):
    """Replaces the guidance paragraph(s) starting at anchor_text with body paragraphs and bullets.

    items: list of str (body paragraph) or ("-", str) (bullet). The first guidance paragraph is reused,
    following guidance paragraphs up to the next heading/table are removed.
    """
    first = find(d, anchor_text)
    # remove the rest of the guidance block (blue paragraphs right after it)
    n = first.getnext()
    while n is not None and n.tag == qn("w:p") and _is_guidance(n):
        nxt = n.getnext()
        n.getparent().remove(n)
        n = nxt
    body_tpl = plain(copy.deepcopy(first))
    bullet_tpl = plain(copy.deepcopy(BULLET_SRC))
    prev = None
    for it in items:
        if isinstance(it, tuple):
            p = copy.deepcopy(bullet_tpl)
            set_par_text(p, it[1])
        else:
            p = copy.deepcopy(body_tpl)
            set_par_text(p, it)
        if prev is None:
            first.addprevious(p)
        else:
            prev.addnext(p)
        prev = p
    first.getparent().remove(first)


def _is_guidance(p):
    for c in p.iter(qn("w:color")):
        if c.get(qn("w:val")) == "0000FF":
            return True
    return False


# ---------------------------------------------------------------- 1. Scope of Testing
write_block("[Describe the scopes of the test", [
    "The target of test is the AgriContract Phase 2 backend (12 business services and the API gateway, SRS v1.7, "
    "SDS) and the React web client. Testing is driven by the seven business flows of the SRS and by the frozen "
    "integration contracts in contracts/ (OpenAPI and event schemas), because most failures in this system happen "
    "between services, not inside one.",
    "In scope:",
    ("-", "BF-01 User Onboarding & KYC, BF-02 Plot & Listing Publication, BF-03 Contract Signing & Activation "
          "(offer, negotiation, OTP e-signature, deposit lock, activation, activation failure and refund, offer "
          "withdrawal), BF-04 Milestone Delivery & Settlement."),
    ("-", "BF-05 Breach & Force Majeure Resolution, BF-06 Mutual Termination & Replacement and BF-07 Emergency "
          "Security Lock, at the depth they are implemented by the end of the project."),
    ("-", "Money and evidence invariants: append-only ledger, one ledger entry per source event, zero remaining "
          "lock before a terminal contract state, two signatures over the same terms hash, audit records written "
          "once per source event."),
    ("-", "Event integration: every published event validates against its frozen schema, every consumer ignores a "
          "duplicate eventId, a malformed payload goes to the service's dead-letter queue without retries."),
    ("-", "Access control: JWT validation and identity headers at the gateway, role checks per endpoint "
          "(BUYER, SELLER, INSPECTOR, OPERATOR, ARBITRATOR, ADMIN, no fallback between roles), fresh login and OTP "
          "for signing."),
    ("-", "Non-functional: running the whole stack on one demo machine and the response-time targets of the SRS "
          "(form submission within 2 s, search within 3 s, bank result applied within 10 s, outbox publish "
          "within 5 s)."),
    "Out of scope: real bank and e-mail providers (replaced by bank-service and MailHog), CA-backed digital "
    "signatures, mobile clients, browser compatibility beyond the latest Chrome and Firefox, penetration testing "
    "and high availability / failover.",
    "Test levels:",
    ("-", "Unit test (UT): in charge each service owner; input is the domain model and use cases of a merged "
          "issue; focus is business rules, state transitions and validation; done when every public rule has a "
          "normal, abnormal and boundary case and the module builds green."),
    ("-", "Integration test (IT): in charge each service owner; input is the service with its real MySQL schema "
          "(Flyway), RabbitMQ and frozen contracts; focus is persistence, outbox publishing, consumers, "
          "idempotency and REST adapters; done when the CI job of the module passes."),
    ("-", "System test (ST): in charge the whole team, led by the contract/escrow owner; input is the full Docker "
          "Compose stack; focus is each business flow end to end through the gateway; done when the flow reaches "
          "its SRS end condition with no open Critical or Major defect."),
    ("-", "Acceptance test (AT): in charge the team with the mentor as reviewer; input is the demo build and the "
          "SRS acceptance criteria; focus is that each role can finish its flows; done when the mentor accepts "
          "the demo of each flow."),
    "Constraints and assumptions: the team has five members and no dedicated tester, so every developer writes "
    "the UT and IT of the code they own and the team runs ST together; GitHub Actions has been unavailable since "
    "07/11/2026 (account billing lock), so the same CI commands are run locally on the pull request head before "
    "merge; test data is synthetic and no real money or personal data is used; the external bank and the "
    "External Verifier are simulated by bank-service and a signing script.",
])

# ---------------------------------------------------------------- 2. Test Strategy
write_block("[List out and describe all testing types", [
    "Every rule is tested at the lowest level that can prove it: business rules in unit tests, anything that "
    "depends on SQL, transactions or the message broker in integration tests against real MySQL and RabbitMQ "
    "containers, and the cooperation of several services in system tests on the full stack. Mocks are used only "
    "for ports at unit level; integration tests never mock the database or the broker, because idempotency and "
    "concurrency bugs only show up there.",
])

write_block("[List out and describe here the testing types", [
    ("-", "Functional testing. Objective: each use case and state transition behaves as the SRS and the state "
          "machines define. Technique: equivalence partitioning and state-transition testing on domain objects "
          "and use cases (UT), REST calls through MockMvc on the real schema (IT), flow scripts through the "
          "gateway (ST). Completion: every allowed transition and every forbidden one has a test; all pass."),
    ("-", "Input validation testing. Objective: invalid requests and payloads are rejected with the frozen error "
          "code and never change state. Technique: boundary value analysis on money (2 decimals, DECIMAL(18,2)), "
          "quantities, dates, OTP (6 digits, 300 s, 5 attempts) and Bean Validation on request DTOs. "
          "Completion: each field limit in the SRS has a below/at/above case."),
    ("-", "Event contract testing. Objective: services stay compatible through the frozen event schemas. "
          "Technique: every emitted event is validated against contracts/events (JSON Schema), malformed "
          "payloads are sent to consumers, duplicates are replayed with the same eventId; a CI job validates "
          "all contract files. Completion: every produced and consumed event type is covered."),
    ("-", "Background job testing. Objective: scheduled work runs once and recovers after failure. Technique: "
          "integration tests with Awaitility for the outbox poller, deposit-lock retry schedule, activation "
          "cancel deadline job, DLQ monitor and business-day deadline calculation. Completion: each job has a "
          "success, retry and no-duplicate case."),
    ("-", "Security testing. Objective: no request reaches a service without a valid identity and role. "
          "Technique: requests without token, with forged identity headers, calling a service directly around "
          "the gateway, with the wrong role, with a stale login for signing, and replayed External Verifier "
          "nonces. Completion: every protected endpoint refuses each case with the frozen status code."),
    ("-", "Performance testing. Objective: the whole stack runs on one demo machine and meets the SRS response "
          "targets for 100 concurrent users. Technique: container resource measurement (docker stats) at idle "
          "and during the demo flow, JMeter load scenarios on the main read and write endpoints. Completion: targets met "
          "or exceeded by no more than 50 %. Executed at the end of the project, after the flows are complete."),
    ("-", "UI testing. Objective: each screen of the web client shows the right data and actions for each role. "
          "Technique: Playwright browser scripts that log in as each role and walk through the business flows, "
          "checked against the SRS screen specifications. Completion: every in-scope screen covered for each "
          "role that can open it."),
])

write_block("<List out and describe here the testing levels", [
    "The table maps each testing type to the levels where it is executed.",
])
tick = "✓"
set_table_rows(next_table(find(d, "The table maps each testing type")), [
    ["Functional Testing", tick, tick, tick, tick],
    ["Input Validation Testing", tick, tick, tick, ""],
    ["Event Contract Testing", tick, tick, "", ""],
    ["Background Job Testing", "", tick, tick, ""],
    ["Security Testing", tick, tick, tick, ""],
    ["Performance Testing", "", "", tick, ""],
    ["UI Testing", "", "", tick, tick],
], header_rows=2, template_row=2)

write_block("<List of the test supporting tools", [
    "Tools used to write, run and report the tests:",
])
set_table_rows(next_table(find(d, "Tools used to write, run and report the tests")), [
    ["Unit and integration test framework (Java)", "JUnit 5 (Jupiter)", "JUnit team (open source)", "5.10.5"],
    ["Mocking of ports and collaborators", "Mockito", "Mockito (open source)", "5.11.0"],
    ["Assertions", "AssertJ", "AssertJ (open source)", "3.25.3"],
    ["Spring context, MockMvc, JPA slice tests", "Spring Boot Test", "VMware / Broadcom", "3.3.5"],
    ["Real MySQL and RabbitMQ in integration tests", "Testcontainers", "Testcontainers (open source)", "1.21.4"],
    ["Waiting on asynchronous results (outbox, consumers, jobs)", "Awaitility", "Awaitility (open source)",
     "4.2.2"],
    ["Validating events against the frozen JSON Schemas", "json-schema-validator", "networknt (open source)",
     "1.5.3"],
    ["Unit and integration tests (NestJS services)", "Jest, @nestjs/testing, Supertest",
     "Meta / NestJS (open source)", "Jest 30, NestJS 11, Supertest 7"],
    ["API and system test collections", "Bruno CLI", "Bruno (open source)", "3.4.2"],
    ["Capturing OTP and notification e-mails", "MailHog", "MailHog (open source)", "latest"],
    ["Load and response-time testing", "Apache JMeter", "Apache Software Foundation", "5.6"],
    ["Automated browser tests of the web client", "Playwright", "Microsoft (open source)", "1.x"],
    ["Contract file validation (OpenAPI 3.1, JSON Schema)", "validate-contracts.py, pytest", "In-house",
     "Python 3"],
    ["Continuous integration", "GitHub Actions (ci.yml)", "GitHub", "-"],
    ["Defect tracking", "GitHub Issues", "GitHub", "-"],
    ["Test case and statistics workbook", "Report-5.1_Unit_Test.xlsx", "In-house", "v1.0"],
])

# ---------------------------------------------------------------- 3. Test Plan
write_block("[List and provide the details about the tools (software, hardware", [
    "All levels run on the developers' machines and on the CI runner; system tests run the full Docker Compose "
    "stack on one machine, which is also the target of the performance test.",
])
set_table_rows(next_table(find(d, "All levels run on the developers' machines")), [
    ["Developer / test machine: 32 CPU threads, 32 GB RAM, Fedora Linux 44", "PC", "-", "-"],
    ["Java runtime and build", "JDK, Maven", "OpenJDK, Apache", "21, 3.9"],
    ["Node runtime (NestJS services)", "Node.js", "OpenJS Foundation", "22 (CI), 24 (local)"],
    ["Container runtime for IT and ST (Docker Engine with Compose)", "Docker", "Docker Inc.", "29.5"],
    ["Database", "MySQL", "Oracle", "8.0"],
    ["Message broker", "RabbitMQ", "Broadcom", "3.13"],
    ["Identity provider (realm agricontract)", "Keycloak", "CNCF", "24.0.4"],
    ["Cache", "Redis", "Redis Ltd.", "7.4"],
    ["Object storage for evidence files (S3 API)", "SeaweedFS", "Open source", "4.48"],
    ["Malware scan of uploads", "ClamAV", "Cisco Talos", "1.4"],
    ["E-mail sink for OTP and notifications", "MailHog", "Open source", "latest"],
    ["CI runner (ubuntu-latest)", "GitHub Actions", "GitHub", "-"],
    ["Browser for UI and acceptance tests", "Chrome, Firefox", "Google, Mozilla", "latest"],
])

write_block("[Separate test milestones", [
    "Test milestones follow the delivery phases of the project plan (P1 27/10–06/11, P2 07/11–17/11, "
    "P3 18/11–30/11).",
])
set_table_rows(next_table(find(d, "Test milestones follow the delivery phases")), [
    ["Test plan and strategy (this document, section I)", "10/11/2026", "12/11/2026"],
    ["Unit test round 1: modules merged by 31/10 (Report-5.1)", "27/10/2026", "31/10/2026"],
    ["Unit test round 2: re-run of every round 1 module (Report-5.1)", "07/11/2026", "07/11/2026"],
    ["Unit test round 1 of modules merged after 31/10, on their merge date (Report-5.1)", "01/11/2026", "30/11/2026"],
    ["Unit and integration tests per pull request (CI or local run before merge)", "21/10/2026", "30/11/2026"],
    ["System test BF-03 Contract Signing & Activation on the full stack", "08/11/2026", "14/11/2026"],
    ["System test BF-01, BF-02", "12/11/2026", "17/11/2026"],
    ["System test BF-04 Milestone Delivery & Settlement", "18/11/2026", "22/11/2026"],
    ["System test BF-05, BF-06, BF-07 (implemented scope)", "22/11/2026", "25/11/2026"],
    ["Performance test on the demo machine", "24/11/2026", "27/11/2026"],
    ["Acceptance test with the mentor (demo of each flow)", "27/11/2026", "30/11/2026"],
    ["Test report (section II)", "27/11/2026", "30/11/2026"],
])

# ---------------------------------------------------------------- change log
fill_change_log(next_table(find(d, "Change Log", tag="w:p")),
                ["10/11/2026", "A", "Minh Cuong", "Section I Overall Plan: scope, test strategy, test levels, "
                                             "supporting tools, test environment and test milestones"])

d.save(DOC)
print("ok")
