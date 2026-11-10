"""SRS 4.3 use case diagrams (UML 2.5 / Visual Paradigm notation), one per human actor plus the contract parties.

Use cases, actors and «include»/«extend» follow the SRS 4.2 table and its relationship table. D42 (08/11/2026): UC29
is started by the parties (cancel request) as well as the Operator (retry the lock). Output: uc_<actor>.drawio/.svg/.png.
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
from ucdiagram import UseCaseDiagram  # noqa: E402

OUT = Path(__file__).resolve().parent
C1, C2 = 235, 440  # use case columns


def guest():
    d = UseCaseDiagram("uc_guest", 440, 270, (100, 15, 420, 255))
    d.actor("guest", 45, 145, "Guest")
    d.usecase("uc01", 260, 85, "UC01 Register Account")
    d.usecase("uc16", 260, 145, "UC16 Browse Listings")
    d.usecase("uc18", 260, 210, "UC18 View Reference\nPrices")
    for u in ("uc01", "uc16", "uc18"):
        d.assoc("guest", u)
    return d


def buyer():
    d = UseCaseDiagram("uc_buyer", 700, 590, (100, 15, 545, 575))
    d.actor("buyer", 45, 310, "Buyer")
    d.usecase("uc02", C1, 70, "UC02 Log In and Log Out")
    d.usecase("uc03", C1, 125, "UC03 Manage Profile\nand KYC")
    d.usecase("uc18", C1, 185, "UC18 View Reference\nPrices")
    d.usecase("uc16", C1, 265, "UC16 Browse Listings")
    d.usecase("uc04", C2, 230, "UC04 View Partner\nProfile")
    d.usecase("uc20", C2, 295, "UC20 Create Contract\nOffer")
    d.usecase("uc33", C1, 395, "UC33 Record Buyer\nReceipt")
    d.usecase("uc34", C2, 360, "UC34 Confirm\nMilestone Delivery")
    d.usecase("uc35", C2, 425, "UC35 Flag Delivery\nIssue")
    d.usecase("uc38", C2, 495, "UC38 Upload\nEvidence File")
    d.usecase("uc30", C1, 535, "UC30 Fund Milestone")
    d.system("bank", 625, 535, "Custodian\nBank")
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
    d = UseCaseDiagram("uc_seller", 700, 735, (100, 15, 545, 720))
    d.actor("seller", 45, 380, "Seller")
    d.usecase("uc02", C1, 65, "UC02 Log In and Log Out")
    d.usecase("uc03", C1, 120, "UC03 Manage Profile\nand KYC")
    d.usecase("uc18", C1, 180, "UC18 View Reference\nPrices")
    d.usecase("uc16", C1, 240, "UC16 Browse Listings")
    d.usecase("uc04", C2, 240, "UC04 View Partner\nProfile")
    d.usecase("uc09", C1, 325, "UC09 Register Plots")
    d.usecase("uc10", C2, 300, "UC10 Upload\nCadastral Extract")
    d.usecase("uc11", C2, 370, "UC11 Request Geo-risk\nRe-evaluation")
    d.usecase("uc13", C1, 410, "UC13 Manage Products")
    d.usecase("uc14", C1, 465, "UC14 Create Listing")
    d.usecase("uc15", C1, 520, "UC15 Pause or Close\nListing")
    d.usecase("uc32", C1, 585, "UC32 Record Seller\nWeighing")
    d.usecase("uc36", C1, 645, "UC36 Respond to\nDelivery Issue")
    d.usecase("uc37", C1, 700 - 0, "UC37 Submit Force\nMajeure Claim")
    d.usecase("uc38", C2, 640, "UC38 Upload\nEvidence File")
    d.system("geo", 625, 335, "Geo Datasets")
    for u in ("uc02", "uc03", "uc18", "uc16", "uc09", "uc13", "uc14", "uc15", "uc32", "uc36", "uc37"):
        d.assoc("seller", u)
    d.extend("uc04", "uc16")
    d.extend("uc10", "uc09")
    d.extend("uc11", "uc09")
    d.assoc("uc10", "geo") if False else None
    d.assoc("uc11", "geo")
    d.include("uc32", "uc38")
    d.include("uc37", "uc38")
    return d


def parties():
    d = UseCaseDiagram("uc_parties", 720, 660, (165, 15, 590, 645))
    d.actor("party", 120, 330, "Contract\nParty")
    d.actor("buyer", 35, 230, "Buyer")
    d.actor("seller", 35, 450, "Seller")
    x1, x2 = 290, 490
    d.usecase("uc21", x1, 70, "UC21 Negotiate\nContract Terms")
    d.usecase("uc22", x1, 140, "UC22 Sign Contract")
    d.usecase("uc23", x2, 190, "UC23 Activate\nContract")
    d.usecase("uc25", x1, 330, "UC25 View Contracts")
    d.usecase("uc24", x2, 255, "UC24 Withdraw\nOffer")
    d.usecase("uc26", x2, 315, "UC26 Propose\nMutual Termination")
    d.usecase("uc27", x2, 380, "UC27 Propose\nContract Replacement")
    d.usecase("uc28", x2, 445, "UC28 Export Escrow\nStatement")
    d.usecase("uc29", x1, 480, "UC29 Handle\nActivation Failure")
    d.usecase("uc31", x1, 545, "UC31 Adjust\nMilestone Price")
    d.usecase("uc40", x1, 605, "UC40 Report Breach")
    d.usecase("uc38", x2, 605, "UC38 Upload\nEvidence File")
    d.system("email", 658, 95, "Email\nService")
    d.system("bank", 658, 190, "Custodian\nBank")
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
    d = UseCaseDiagram("uc_inspector", 700, 380, (130, 15, 545, 365))
    d.actor("insp", 55, 145, "Inspector\n(Level 1.5)")
    d.usecase("uc02", 255, 70, "UC02 Log In and Log Out")
    d.usecase("uc03", 255, 130, "UC03 Manage Profile\nand KYC")
    d.usecase("uc43", 255, 200, "UC43 View Assigned\nCases")
    d.usecase("uc44", C2, 260, "UC44 Submit\nInspection Result")
    d.usecase("uc46", C2, 325, "UC46 Send External\nInspection Report")
    d.actor("l2", 640, 300, "Level 2\nInspection\nOrganization")
    d.system("email", 630, 120, "Email\nService")
    for u in ("uc02", "uc03", "uc43"):
        d.assoc("insp", u)
    d.extend("uc44", "uc43")
    d.assoc("l2", "uc46")
    d.assoc("uc46", "email")
    return d


def operator():
    d = UseCaseDiagram("uc_operator", 700, 560, (100, 15, 545, 545))
    d.actor("op", 45, 290, "Operator")
    ys = [65, 120, 175, 230, 285, 340, 395, 450, 505]
    items = [("uc02", "UC02 Log In and Log Out"), ("uc05", "UC05 Review KYC\nApplication"),
             ("uc12", "UC12 Review Plot Risk"), ("uc17", "UC17 Moderate\nCategories and Listings"),
             ("uc19", "UC19 Enter Manual Prices"), ("uc06", "UC06 Propose\nReputation Action"),
             ("uc29", "UC29 Handle Activation\nFailure (retry the lock)"), ("uc39", "UC39 Retry\nMilestone Funding"),
             ("uc47", "UC47 Review External\nInspection Report")]
    for (id, text), y in zip(items, ys):
        d.usecase(id, 300, y, text)
        d.assoc("op", id)
    d.system("bank", 625, 422, "Custodian\nBank")
    d.actor("l2", 640, 480, "Level 2\nInspection Org")
    d.assoc("uc29", "bank")
    d.assoc("uc39", "bank")
    d.assoc("uc47", "l2")
    return d


def admin():
    d = UseCaseDiagram("uc_admin", 460, 380, (100, 15, 440, 365))
    d.actor("admin", 45, 195, "Admin")
    for (id, text), y in zip([("uc02", "UC02 Log In and Log Out"), ("uc08", "UC08 Manage Accounts\nand Roles"),
                              ("uc48", "UC48 Export Contract\nEvidence"), ("uc49", "UC49 Monitor Audit\nIntegrity"),
                              ("uc50", "UC50 View Security\nLock Status"), ("uc53", "UC53 View Analytics\nReports")],
                             [65, 120, 180, 240, 300, 355 - 10]):
        d.usecase(id, 270, y, text)
        d.assoc("admin", id)
    return d


def arbitrator():
    d = UseCaseDiagram("uc_arbitrator", 700, 400, (100, 15, 545, 385))
    d.actor("arb", 45, 210, "Arbitrator")
    d.usecase("uc02", C1, 70, "UC02 Log In and Log Out")
    d.usecase("uc07", C1, 130, "UC07 Approve\nReputation Action")
    d.usecase("uc41", C1, 205, "UC41 Review and\nResolve Breach Case")
    d.usecase("uc45", C2, 205, "UC45 Commission\nExternal Inspection")
    d.usecase("uc42", C1, 285, "UC42 Decide Force\nMajeure Claim")
    d.usecase("uc54", C1, 350, "UC54 Terminate\nContract")
    d.actor("l2", 640, 160, "Level 2\nInspection Org")
    d.system("bank", 625, 320, "Custodian\nBank")
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
