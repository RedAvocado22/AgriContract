"""Figure 12. Class diagram — dispute, inspection and remedy (UML / Visual Paradigm notation).

inspection-service assignment is drawn from the code on main (10/11/2026): Level15RequestedConsumer,
ManageLevel15Assignments, InspectorEligibilityPort, AssignmentLifecycleRepository, TimedAssignment, InspectionHashes.
Report submission (SubmitInspectionReportUseCase, InspectionReport) and the contract-service dispute classes are the
design (dispute is not built yet).
"""
import sys
from pathlib import Path

from classfig import Diagram, link

OUT = Path(__file__).resolve().parent


def build():
    d = Diagram("fig12_class_dispute", 920, 1300, allow_crossings=True)
    d.package("pc", 10, 30, 900, 660, "contract-service (design)")
    d.klass("routing", 150, 50, "DisputeRoutingService", "control", attrs=[], ops=[
        "+ route(milestone : Milestone,", "      terms : InspectionTerms) : InspectionTier"])
    d.klass("respond", 460, 50, "RespondToMilestoneFlagUseCase", "control", attrs=[], ops=[
        "+ execute(cmd : RespondFlagCommand) : void"])
    d.klass("flag", 770, 50, "FlagMilestoneIssueUseCase", "control", attrs=[], ops=[
        "+ execute(cmd : FlagIssueCommand) : void"])
    d.klass("ms", 460, 180, "Milestone", "entity", attrs=[], ops=["+ flag(issue : IssueType) : void",
                                                              "+ contest() : void"])
    d.klass("breach", 770, 180, "BreachCase", "entity", attrs=[
        "- status : BreachCaseStatus", "- contestStatus : ContestStatus", "- contestDeadline : Instant [0..1]"],
        ops=["+ BreachCase(milestoneId : UUID,", "      reason : BreachReason)",
             "+ resolve(decision : AttributionDecision)", "      : void"])
    d.klass("apply", 770, 390, "ApplyInspectionResultUseCase", "control", attrs=[], ops=[
        "+ execute(cmd : ApplyResultCommand) : void", "- verifyResultHash(", "      result : InspectionSettlementResultV1)",
        "      : void"])
    d.klass("attr", 440, 340, "AttributionDecision", "entity", attrs=[
        "- decisionSource : DecisionSource", "- finalBreachingRole : Role [0..1]",
        "- qualityDisposition : QualityDisposition [0..1]"], ops=[
        "+ AttributionDecision(source : DecisionSource,", "      role : Role, disposition : QualityDisposition)"])
    d.klass("remedy", 140, 360, "RemedyDecision", "entity", attrs=[
        "- penaltyEligible : boolean", "- reputationEligible : boolean"], ops=[])
    d.klass("leg", 140, 520, "RemedyLeg", "entity", attrs=[
        "- remedyLegId : UUID", "- remedyType : RemedyType", "- amount : Money"], ops=[])
    d.klass("rcalc", 500, 550, "RemedyCalculator", "control", attrs=[], ops=[
        "+ calculate(decision : AttributionDecision,", "      profile : LegalProfile) : RemedyDecision"])
    d.klass("qdc", 790, 550, "QualityDispositionCalculator", "control", attrs=[], ops=[
        "+ evaluate(spec : QualitySpec,", "      metrics : ActualMetrics)", "      : QualityDisposition"])
    # ---------- inspection-service ----------
    d.package("pi", 10, 720, 900, 560, "inspection-service (assignment: code; report: design)")
    d.klass("cons", 150, 740, "Level15RequestedConsumer", "control", attrs=[], ops=[
        "+ accept(message : Message) : void"])
    d.klass("manage", 470, 740, "ManageLevel15Assignments", "control", attrs=[], ops=[
        "+ requested(request :", "      Level15AssignmentRequestedCommand) : void",
        "+ timeout(commissionId : String) : void", "+ closeConfirmed(contract : String,",
        "      milestone : String, commission : String,", "      inspector : String, confirmedAt : Instant) : void"])
    d.klass("elig", 785, 740, "InspectorEligibilityPort", "interface", attrs=[], ops=[
        "+ load(authenticatedUserId : String)", "      : InspectorEligibility",
        "+ loadEligibleInspectors() : List<String>"])
    d.klass("submit", 150, 920, "SubmitInspectionReportUseCase", "control", attrs=[], ops=[
        "+ execute(cmd : SubmitReportCommand) : void"])
    d.klass("repo", 470, 960, "AssignmentLifecycleRepository", "interface", attrs=[], ops=[
        "+ lock(contractId : String, milestoneId : String)", "+ active(contractId : String,",
        "      milestoneId : String) : Optional<TimedAssignment>", "+ insert(assignment : TimedAssignment)",
        "+ end(commissionId : String, endedAt : Instant)", "      : boolean"])
    d.klass("timed", 785, 960, "TimedAssignment", "record", attrs=[
        "+ commissionId : String", "+ contractId : String", "+ milestoneId : String", "+ inspectorId : String",
        "+ assignedAt : Instant", "+ reportDueAt : Instant"], ops=None)
    d.klass("report", 150, 1050, "InspectionReport", "entity", attrs=["- resultHash : String",
                                                                     "- reportHash : String"], ops=[])
    d.klass("hashes", 150, 1170, "InspectionHashes", "", attrs=[], ops=[
        "+ calculateResultHash(result) : String", "+ calculateReportHash(report) : String"])
    link(d, "association", "respond", "l", "routing", "r")
    link(d, "association", "respond", "b", "ms", "t")
    link(d, "association", "flag", "l", "ms", "r", (610, 120), (610, 230), s_off=120 - 93)
    link(d, "dependency", "flag", "b", "breach", "t", label="«create»", side="r")
    link(d, "association", "apply", "t", "breach", "b")
    link(d, "dependency", "apply", "l", "attr", "r", s_off=430 - 455.5, d_off=430 - 405.5, label="«create»",
         side="t")
    link(d, "association", "apply", "l", "rcalc", "t", (500, 500), s_off=500 - 455.5)
    link(d, "association", "apply", "b", "qdc", "t")
    link(d, "association", "breach", "l", "attr", "t", (540, 310), s_off=310 - 260.5, label="resolved by 0..1",
         side="t")
    link(d, "association", "attr", "l", "remedy", "r", label="produces 0..1", side="t")
    link(d, "composition", "remedy", "b", "leg", "t", label="legs 1..*", side="r")
    link(d, "association", "cons", "r", "manage", "l")
    link(d, "association", "manage", "r", "elig", "l")
    link(d, "association", "manage", "b", "repo", "t")
    link(d, "association", "repo", "r", "timed", "l")
    link(d, "association", "submit", "r", "manage", "l", (300, 963), (300, 880), s_off=0)
    link(d, "dependency", "submit", "b", "report", "t", label="«create»", side="r")
    link(d, "association", "submit", "b", "hashes", "t", s_off=100, d_off=100)
    return d


if __name__ == "__main__":
    d = build()
    d.write(OUT)
    print("ok")
