"""Figure 9. Class diagram — milestone settlement across contract, escrow and bank (UML / Visual Paradigm notation).

bank-service and the shared eventing library are drawn from the code on main (10/11/2026): LedgerCommandConsumer,
ProcessLedgerCommandUseCase, LedgerEntry (static create), LedgerEntryRepository, LedgerResultPublisher, OutboxWriter,
ProcessedEventStore. The contract-service and escrow-service classes are the design (milestone settlement is not
built yet). Static members are underlined.
"""
import sys
from pathlib import Path

from classfig import Diagram, link

OUT = Path(__file__).resolve().parent


def build():
    d = Diagram("fig09_class_milestone", 900, 1250, allow_crossings=True)
    # ---------- contract-service (design) ----------
    d.package("pc", 10, 30, 880, 560, "contract-service (design)")
    d.klass("ctrl", 330, 50, "MilestoneController", "control", attrs=[], ops=[
        "+ weigh(mid : UUID, req : WeighRequest) : void",
        "+ confirm(mid : UUID, req : ConfirmRequest) : void"])
    d.klass("weigh", 150, 190, "RecordSellerWeighedUseCase", "control", attrs=[], ops=[
        "+ execute(cmd : RecordWeighCommand) : void"])
    d.klass("confirm", 470, 190, "ConfirmMilestoneCleanUseCase", "control", attrs=[], ops=[
        "+ execute(cmd : ConfirmCleanCommand) : void"])
    d.klass("calc", 745, 190, "SettlementCalculator", "control", attrs=[], ops=[
        "+ compute(terms : ContractTerms,", "      seller : Quantity,", "      buyer : Quantity) : Settlement"])
    d.klass("ms", 150, 310, "Milestone", "entity", attrs=[
        "- milestoneId : UUID", "- status : MilestoneStatus", "- batchAmount : Money",
        "- sellerDeclaredWeight : Quantity", "- buyerReceivedWeight : Quantity"], ops=[
        "+ recordSellerWeighed(weight : Quantity,", "      evidence : UUID) : void",
        "+ recordBuyerReceived(weight : Quantity,", "      evidence : UUID) : void",
        "+ settle(settlement : Settlement) : void"])
    d.klass("sync", 500, 320, "MilestoneSyncOutbox", "entity", attrs=[], ops=[
        "+ add(milestoneId : UUID) : void", "+ nextUnprocessed() : List<UUID>"])
    d.klass("recon", 735, 330, "CompletionReconciler", "control", attrs=[], ops=[
        "+ run() : void", "- expectedLegsComplete(", "      entries : List<LedgerEntryView>)", "      : boolean"])
    d.klass("port", 735, 480, "BankLedgerQueryPort", "interface", attrs=[], ops=[
        "+ entriesOf(contractId : UUID)", "      : List<LedgerEntryView>"])
    d.klass("contract", 500, 480, "Contract", "entity", attrs=[], ops=[
        "+ transitionTo(next : ContractStatus) : void"])
    link(d, "association", "ctrl", "b", "weigh", "t", s_off=-80, d_off=100)
    link(d, "association", "ctrl", "b", "confirm", "t", s_off=80, d_off=-60)
    link(d, "association", "weigh", "b", "ms", "t")
    link(d, "association", "confirm", "r", "calc", "l")
    link(d, "association", "confirm", "b", "ms", "r", (370, 380), s_off=-100)
    link(d, "association", "confirm", "b", "sync", "t", s_off=30, d_off=0)
    link(d, "association", "recon", "l", "sync", "r")
    link(d, "association", "recon", "b", "port", "t")
    link(d, "association", "recon", "l", "contract", "t", (580, 445), s_off=445 - 395.5)
    # ---------- escrow-service (design), shared eventing and bank-service (code) ----------
    d.package("pe", 10, 640, 300, 250, "escrow-service (design)")
    d.klass("cons", 160, 660, "MilestoneSettledConsumer", "control", attrs=[], ops=[
        "+ on(message : Message) : void"])
    d.klass("release", 160, 770, "ReleaseMilestoneFundsUseCase", "control", attrs=[], ops=[
        "+ execute(cmd : ReleaseFundsCommand)", "      : void"])
    link(d, "association", "cons", "b", "release", "t")
    d.package("pv", 340, 640, 550, 250, "eventing (shared library, code)")
    d.klass("outbox", 470, 660, "OutboxWriter", "", attrs=[], ops=["+ append(envelope : EventEnvelope)", "      : void"])
    d.klass("inbox", 740, 660, "ProcessedEventStore", "", attrs=[], ops=[
        "+ markProcessed(consumerName : String,", "      envelope : EventEnvelope) : boolean"])
    d.package("pb", 10, 940, 880, 290, "bank-service (code)")
    d.klass("lcc", 150, 960, "LedgerCommandConsumer", "control", attrs=[], ops=[
        "+ onLockRequested(message : Message)", "+ onReleaseRequested(message : Message)",
        "+ onRefundRequested(message : Message)"])
    d.klass("plcu", 450, 960, "ProcessLedgerCommandUseCase", "interface", attrs=[], ops=[
        "+ execute(command : LedgerCommand) : void"])
    d.klass("repo", 750, 960, "LedgerEntryRepository", "interface", attrs=[], ops=[
        "+ findBySourceEventId(sourceEventId : String)", "      : Optional<LedgerEntry>",
        "+ append(entry : LedgerEntry) : void"])
    d.klass("pub", 450, 1070, "LedgerResultPublisher", "interface", attrs=[], ops=[
        "+ completed(command : LedgerCommand,", "      entry : LedgerEntry) : void"])
    d.klass("entry", 750, 1095, "LedgerEntry", "entity", attrs=[
        "- sourceEventId : String", "- entryType : EntryType", "- amount : Money"], ops=[
        "__+ create(...) : LedgerEntry"])
    link(d, "association", "lcc", "r", "plcu", "l")
    link(d, "association", "plcu", "r", "repo", "l")
    link(d, "association", "plcu", "b", "pub", "t")
    link(d, "dependency", "plcu", "r", "entry", "l", (600, 1028), (600, 1140), s_off=25, label="«create»", seg=1,
         side="r")
    link(d, "dependency", "pc", "b", "pv", "t", s_off=600 - 430, d_off=600 - 595)
    link(d, "dependency", "pe", "r", "pv", "l")
    link(d, "dependency", "pb", "t", "pv", "b", s_off=600 - 430, d_off=600 - 595)
    return d


if __name__ == "__main__":
    d = build()
    for n in d.nodes.values():
        print(n.id, [round(v) for v in n.box()])
    if sys.argv[1:] != ["measure"]:
        d.write(OUT)
        print("ok")
