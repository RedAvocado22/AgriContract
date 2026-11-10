"""SRS 4.3 use case diagrams (UML 2.5 / Visual Paradigm notation), one per human actor plus the contract parties.

Use cases, actors and «include»/«extend» follow the SRS 4.2 table and its relationship table. D42 (08/11/2026): UC29
is started by the parties (cancel request) as well as the Operator (retry the lock). Output: uc_<actor>.drawio/.svg/.png.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from ucdiagram import UseCaseDiagram  # noqa: E402

OUT = Path(__file__).resolve().parent


def guest():
    d = UseCaseDiagram("uc_guest", 440, 270, (100, 15, 420, 255))
    d.actor("guest", 45, 145, "Guest")
    d.usecase("uc01", 260, 85, "UC01 Register Account")
    d.usecase("uc16", 260, 145, "UC16 Browse Listings")
    d.usecase("uc18", 260, 210, "UC18 View Reference\nPrices")
    for u in ("uc01", "uc16", "uc18"):
        d.assoc("guest", u)
    return d


L1, L2 = 150, 430  # left edge of the use case columns (inside a boundary starting at x=100)


def cy(d, id):
    return d.els[id].cy


def buyer():
    d = UseCaseDiagram("uc_buyer", 730, 635, (100, 15, 600, 620))
    d.actor("buyer", 45, 310, "Buyer")
    d.stack(L1, 45, [("uc02", "UC02 Log In and Log Out"), ("uc03", "UC03 Manage Profile\nand KYC"),
                     ("uc18", "UC18 View Reference\nPrices"), ("uc16", "UC16 Browse Listings")])
    d.stack(L2, 190, [("uc04", "UC04 View Partner\nProfile"), ("uc20", "UC20 Create Contract\nOffer")])
    d.stack(L1, 363, [("uc33", "UC33 Record Buyer\nReceipt")])
    d.stack(L2, 330, [("uc34", "UC34 Confirm\nMilestone Delivery"), ("uc35", "UC35 Flag Delivery\nIssue")])
    d.stack(L2, 492, [("uc38", "UC38 Upload\nEvidence File")])
    d.stack(L1, 565, [("uc30", "UC30 Fund Milestone")])
    d.system("bank", 668, cy(d, "uc30"), "Custodian\nBank")
    for u in ("uc02", "uc03", "uc18", "uc16", "uc33", "uc30"):
        d.assoc("buyer", u)
    d.extend("uc04", "uc16")
    d.extend("uc20", "uc16")
    d.extend("uc34", "uc33")
    d.extend("uc35", "uc33")
    d.include("uc35", "uc38")
    d.assoc("uc30", "bank")
    return d


def seller():
    d = UseCaseDiagram("uc_seller", 730, 765, (100, 15, 610, 750))
    d.actor("seller", 45, 390, "Seller")
    d.stack(L1, 45, [("uc02", "UC02 Log In and Log Out"), ("uc03", "UC03 Manage Profile\nand KYC"),
                     ("uc18", "UC18 View Reference\nPrices"), ("uc16", "UC16 Browse Listings")])
    d.stack(L2, 222, [("uc04", "UC04 View Partner\nProfile")])
    d.stack(L2, 292, [("uc10", "UC10 Upload\nCadastral Extract"), ("uc11", "UC11 Request Geo-risk\nRe-evaluation")])
    d.stack(L1, (cy(d, "uc10") + cy(d, "uc11")) / 2 - 18.5, [("uc09", "UC09 Register Plots")])
    d.stack(L1, d.els["uc09"].cy + 18.5 + 14, [
        ("uc13", "UC13 Manage Products"), ("uc14", "UC14 Create Listing"), ("uc15", "UC15 Pause or Close\nListing"),
        ("uc32", "UC32 Record Seller\nWeighing"), ("uc36", "UC36 Respond to\nDelivery Issue"),
        ("uc37", "UC37 Submit Force\nMajeure Claim")])
    d.stack(L2, (cy(d, "uc32") + cy(d, "uc37")) / 2 - 26, [("uc38", "UC38 Upload\nEvidence File")])
    d.system("geo", 675, cy(d, "uc11"), "Geo Datasets")
    for u in ("uc02", "uc03", "uc18", "uc16", "uc09", "uc13", "uc14", "uc15", "uc32", "uc36", "uc37"):
        d.assoc("seller", u)
    d.extend("uc04", "uc16")
    d.extend("uc10", "uc09")
    d.extend("uc11", "uc09")
    d.assoc("uc11", "geo")
    d.include("uc32", "uc38")
    d.include("uc37", "uc38")
    return d


def parties():
    d = UseCaseDiagram("uc_parties", 790, 640, (165, 15, 650, 625))
    d.actor("party", 95, 335, "Contract\nParty")
    d.actor("buyer", 95, 165, "Buyer")
    d.actor("seller", 95, 505, "Seller")
    a1, a2 = 215, 465
    d.stack(a1, 45, [("uc21", "UC21 Negotiate\nContract Terms"), ("uc22", "UC22 Sign Contract")])
    d.stack(a2, cy(d, "uc22") + 40, [("uc23", "UC23 Activate\nContract")])
    d.stack(a2, d.els["uc23"].cy + 46, [("uc24", "UC24 Withdraw\nOffer"), ("uc26", "UC26 Propose\nMutual Termination"),
                                        ("uc27", "UC27 Propose\nContract Replacement"),
                                        ("uc28", "UC28 Export Escrow\nStatement")], gap=20)
    d.stack(a1, (cy(d, "uc26") + cy(d, "uc27")) / 2 - 18.5, [("uc25", "UC25 View Contracts")])
    d.stack(a1, max(d.els["uc25"].cy + 18.5, d.els["uc27"].cy) + 14, [
        ("uc29", "UC29 Handle\nActivation Failure"), ("uc31", "UC31 Adjust\nMilestone Price"),
        ("uc40", "UC40 Report Breach")])
    d.stack(a2, cy(d, "uc40") - 26, [("uc38", "UC38 Upload\nEvidence File")])
    d.system("email", 718, cy(d, "uc22") - 30, "Email\nService")
    d.system("bank", 718, cy(d, "uc23") + 10, "Custodian\nBank")
    d.generalize("buyer", "party")
    d.generalize("seller", "party")
    for u in ("uc21", "uc22", "uc25", "uc29", "uc31", "uc40"):
        d.assoc("party", u)
    d.extend("uc23", "uc22")
    for u in ("uc24", "uc26", "uc27", "uc28"):
        d.extend(u, "uc25")
    d.include("uc40", "uc38")
    d.assoc("uc22", "email")
    d.assoc("uc23", "bank")
    return d


def inspector():
    d = UseCaseDiagram("uc_inspector", 730, 400, (130, 15, 600, 385))
    d.actor("insp", 55, 150, "Inspector\n(Level 1.5)")
    d.stack(175, 45, [("uc02", "UC02 Log In and Log Out"), ("uc03", "UC03 Manage Profile\nand KYC"),
                      ("uc43", "UC43 View Assigned\nCases")])
    d.stack(L2, 210, [("uc44", "UC44 Submit\nInspection Result")])
    d.stack(L2, 290, [("uc46", "UC46 Send External\nInspection Report")])
    d.system("email", 668, 215, "Email\nService")
    d.actor("l2", 668, 335, "Level 2\nInspection\nOrganization")
    for u in ("uc02", "uc03", "uc43"):
        d.assoc("insp", u)
    d.extend("uc44", "uc43")
    d.assoc("l2", "uc46")
    d.assoc("uc46", "email")
    return d


def operator():
    d = UseCaseDiagram("uc_operator", 730, 625, (100, 15, 600, 610))
    d.actor("op", 45, 310, "Operator")
    items = [("uc02", "UC02 Log In and Log Out"), ("uc05", "UC05 Review KYC\nApplication"),
             ("uc12", "UC12 Review Plot Risk"), ("uc17", "UC17 Moderate\nCategories and Listings"),
             ("uc19", "UC19 Enter Manual Prices"), ("uc06", "UC06 Propose\nReputation Action"),
             ("uc29", "UC29 Handle Activation\nFailure (retry the lock)"), ("uc39", "UC39 Retry\nMilestone Funding"),
             ("uc47", "UC47 Review External\nInspection Report")]
    d.stack(L1, 45, items)
    for id, _ in items:
        d.assoc("op", id)
    d.system("bank", 668, (cy(d, "uc29") + cy(d, "uc39")) / 2, "Custodian\nBank")
    d.actor("l2", 668, cy(d, "uc47") + 12, "Level 2\nInspection Org")
    d.assoc("uc29", "bank")
    d.assoc("uc39", "bank")
    d.assoc("uc47", "l2")
    return d


def admin():
    d = UseCaseDiagram("uc_admin", 400, 440, (100, 15, 380, 425))
    d.actor("admin", 45, 225, "Admin")
    items = [("uc02", "UC02 Log In and Log Out"), ("uc08", "UC08 Manage Accounts\nand Roles"),
             ("uc48", "UC48 Export Contract\nEvidence"), ("uc49", "UC49 Monitor Audit\nIntegrity"),
             ("uc50", "UC50 View Security\nLock Status"), ("uc53", "UC53 View Analytics\nReports")]
    d.stack(L1, 45, items)
    for id, _ in items:
        d.assoc("admin", id)
    return d


def arbitrator():
    d = UseCaseDiagram("uc_arbitrator", 730, 375, (100, 15, 600, 360))
    d.actor("arb", 45, 200, "Arbitrator")
    d.stack(L1, 45, [("uc02", "UC02 Log In and Log Out"), ("uc07", "UC07 Approve\nReputation Action"),
                     ("uc41", "UC41 Review and\nResolve Breach Case"), ("uc42", "UC42 Decide Force\nMajeure Claim"),
                     ("uc54", "UC54 Terminate\nContract")])
    d.stack(L2, 80, [("uc45", "UC45 Commission\nExternal Inspection")])
    d.actor("l2", 668, cy(d, "uc45") + 12, "Level 2\nInspection Org")
    d.system("bank", 668, (cy(d, "uc41") + cy(d, "uc54")) / 2 + 20, "Custodian\nBank")
    for u in ("uc02", "uc07", "uc41", "uc42", "uc54"):
        d.assoc("arb", u)
    d.extend("uc45", "uc41")
    d.assoc("uc45", "l2")
    d.assoc("uc41", "bank")
    d.assoc("uc54", "bank")
    return d


def verifier():
    d = UseCaseDiagram("uc_verifier", 640, 210, (120, 15, 470, 195))
    d.actor("ver", 55, 110, "External\nVerifier")
    d.usecase("uc51", 290, 75, "UC51 Query Audit Hash")
    d.usecase("uc52", 290, 145, "UC52 Lock or Unlock\nMonetary Operations")
    d.system("bank", 560, 145, "Custodian\nBank")
    d.assoc("ver", "uc51")
    d.assoc("ver", "uc52")
    d.assoc("uc52", "bank")
    return d


if __name__ == "__main__":
    failed = []
    for build in (guest, buyer, seller, parties, inspector, operator, admin, arbitrator, verifier):
        try:
            build().write(OUT)
            print("ok", build.__name__)
        except SystemExit as e:
            failed.append(str(e))
    if failed:
        print("\n".join(failed))
        sys.exit(1)
