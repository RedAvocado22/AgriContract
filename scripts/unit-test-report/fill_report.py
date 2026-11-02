# Fill the Report 5.1 Unit Test template through LibreOffice (UNO) so charts,
# images, comments and validation survive. Test cases come from data_*.py; Round 1
# results come from surefire, so run `mvn test` in backend/ first.
#
#   soffice --headless --accept="socket,host=127.0.0.1,port=2002;urp;" &
#   python3 fill_report.py template/Report-5.1_Unit_Test_template.xlsx \
#       ../../docs/reports/Report-5.1_Unit_Test.xlsx ../../backend
#
# A sheet whose row count differs from its test class's surefire count stops the run.
import glob, os, re, sys, time
import uno
from com.sun.star.beans import PropertyValue
from all_modules import MODULES

TEST_DATE, TESTER = "31/10/2026", "RedAvocado22"
PROJECT_CODE = "AGC"
SRC, OUT, BACKEND = (os.path.abspath(p) for p in sys.argv[1:4])


def surefire_results():
    results = {}
    for path in glob.glob(f"{BACKEND}/**/target/surefire-reports/TEST-*.xml", recursive=True):
        head = open(path, encoding="utf-8").read(4000)
        suite = re.search(r"<testsuite [^>]*", head).group(0)
        attr = lambda k: re.search(k + r'="([^"]*)"', suite).group(1)
        results[attr("name").split(".")[-1]] = (
            int(attr("tests")), int(attr("failures")) + int(attr("errors")), int(attr("skipped")))
    return results


# Round 1 = the latest mvn test run; every row of a sheet maps to one test (or one invocation)
RESULTS = surefire_results()
for m in MODULES:
    rows = sum(len(cases) for _, cases in m["groups"])
    tests, failed, skipped = RESULTS[m["test_class"]]
    if (tests, failed, skipped) != (rows, 0, 0):
        sys.exit(f"{m['sheet']}: {rows} rows vs surefire {tests} tests, {failed} failed, {skipped} skipped")


def prop(name, value):
    p = PropertyValue()
    p.Name, p.Value = name, value
    return p


def connect():
    local = uno.getComponentContext()
    resolver = local.ServiceManager.createInstanceWithContext(
        "com.sun.star.bridge.UnoUrlResolver", local)
    for _ in range(60):
        try:
            return resolver.resolve(
                "uno:socket,host=127.0.0.1,port=2002;urp;StarOffice.ComponentContext")
        except Exception:
            time.sleep(1)
    raise RuntimeError("soffice not reachable")


ctx = connect()
desktop = ctx.ServiceManager.createInstanceWithContext("com.sun.star.frame.Desktop", ctx)
doc = desktop.loadComponentFromURL(uno.systemPathToFileUrl(SRC), "_blank", 0, (prop("Hidden", True),))
sheets = doc.Sheets


def cell(sh, ref):
    return sh.getCellRangeByName(ref)


def link_to_sheet(c, sheet_name):
    # template styles these cells as links but carries no target
    c.setString("")
    field = doc.createInstance("com.sun.star.text.TextField.URL")
    field.Representation = sheet_name
    field.URL = f"#{sheet_name}.A1"
    c.insertTextContent(c.getText().createTextCursor(), field, False)


MM = 100  # UNO lengths are 1/100 mm


def set_widths(sh, widths_mm):
    # widths_mm: {column index: width in mm}
    for col, w in widths_mm.items():
        sh.Columns.getByIndex(col).Width = w * MM


def fit_rows(sh, first_row, last_row, last_col, skip=()):
    # rows are 1-based; LibreOffice's optimal height runs tight, so leave headroom
    for r in range(first_row, last_row + 1):
        if r in skip:
            continue
        sh.getCellRangeByPosition(0, r - 1, last_col, r - 1).IsTextWrapped = True
        row = sh.Rows.getByIndex(r - 1)
        row.OptimalHeight = True
        row.Height = int(row.Height * 1.08) + 60


def fit_page_width(sh):
    style = doc.StyleFamilies.getByName("PageStyles").getByName(sh.PageStyle)
    style.ScaleToPagesX = 1
    style.ScaleToPagesY = 0


def copy_row(sh, src_row, dst_row, last_col):
    # rows are 1-based here; copyRange carries borders, wrap and validation
    src = sh.getCellRangeByPosition(0, src_row - 1, last_col, src_row - 1).getRangeAddress()
    sh.copyRange(sh.getCellByPosition(0, dst_row - 1).getCellAddress(), src)


# ── Cover ────────────────────────────────────────────────────────────────────
cover = sheets.getByName("Cover")
cell(cover, "B4").setString("AgriContract")
cell(cover, "B5").setString(PROJECT_CODE)
cell(cover, "B6").setFormula('=B5&"_UnitTest_v1.0"')
cell(cover, "F4").setString(TESTER)
cell(cover, "F5").setString(TEST_DATE)
cell(cover, "F6").setString("v1.0")
for ref, text in zip(["A11", "B11", "C11", "D11", "E11", "F11"],
                     [TEST_DATE, "v1.0", "Toàn bộ tài liệu", "A",
                      "Bản đầu, lấy từ unit test trong backend/", "SRS v1.6"]):
    cell(cover, ref).setString(text)

# ── Module sheets: one copy of ModuleName1 per test class ───────────────────
STYLE_HEADER, STYLE_CASE = 1100, 1101
for i, m in enumerate(MODULES):
    sheets.copyByName("ModuleName1", m["sheet"], sheets.Count)
    sh = sheets.getByName(m["sheet"])
    copy_row(sh, 12, STYLE_HEADER, 19)
    copy_row(sh, 13, STYLE_CASE, 19)
    sh.getCellRangeByName("A12:P1003").clearContents(1 | 2 | 4 | 16)
    cell(sh, "B2").setString(m["sheet"])
    # template counts Boundary from E10 while Normal/Abnormal start at E12
    cell(sh, "D9").setFormula("=COUNTIF($E12:$E1003;D8)")
    # status dropdown list (T2:T4) has no "Failed"
    cell(sh, "T5").setString("Failed")

    row, n, header_rows = 12, 0, set()
    for method, cases in m["groups"]:
        copy_row(sh, STYLE_HEADER, row, 19)
        cell(sh, f"A{row}").setString(method)
        header_rows.add(row)
        row += 1
        for pre, inp, ret, exc, typ in cases:
            n += 1
            copy_row(sh, STYLE_CASE, row, 19)
            values = [f"UTC{n:03d}", pre, inp,
                      f"Return: {ret}\nException: {exc}\nLog message: N/A",
                      typ, "Passed", TEST_DATE, TESTER, "", "", "", "", "", ""]
            for col, v in enumerate(values):
                sh.getCellByPosition(col, row - 1).setString(v)
            v = sh.getCellByPosition(5, row - 1).Validation
            v.setFormula1("$T$2:$T$5")
            for c in (5, 8, 11):
                sh.getCellByPosition(c, row - 1).Validation = v
            row += 1
    # leftover sample rows below the data
    if row <= 24:
        sh.getCellRangeByPosition(0, row - 1, 15, 23).clearContents(1 | 2 | 4 | 16)
    sh.Rows.removeByIndex(STYLE_HEADER - 1, 2)
    # ID, Pre-conditions, Input, Expected, Type, Round 1, Test date, Tester
    set_widths(sh, {0: 32, 1: 45, 2: 55, 3: 90, 4: 20, 5: 18, 6: 22, 7: 26})
    fit_rows(sh, 13, row - 1, 15, skip=header_rows)

sheets.removeByName("ModuleName1")

# ── Test Cases ───────────────────────────────────────────────────────────────
tc = sheets.getByName("Test Cases")
cell(tc, "D3").setFormula("=$Cover.B4")
cell(tc, "D4").setFormula("=$Cover.B5")
cell(tc, "D5").setString(
    "1. OS: Linux (Fedora 44)\n2. JDK: OpenJDK 21.0.7, Maven 3.9.16\n"
    "3. Spring Boot 3.3.5, JUnit 5, Mockito, AssertJ\n"
    "4. Không cần DB/RabbitMQ: dependency ngoài được mock hoặc fake in-memory\n"
    "5. Chạy: mvn test trong backend/; kết quả lấy từ target/surefire-reports\n"
    "6. Ngoài phạm vi: test dựng Spring context, Testcontainers, MySQL thật (integration)")
for i, m in enumerate(MODULES):
    r = 9 + i
    if r > 10:
        copy_row(tc, 10, r, 5)
    tc.getCellByPosition(1, r - 1).setValue(i + 1)
    for col, text in [(2, m["module"]), (4, m["desc"]), (5, m["pre"])]:
        tc.getCellByPosition(col, r - 1).setString(text)
    link_to_sheet(tc.getCellByPosition(3, r - 1), m["sheet"])
# No, Module Name, Sheet Name, Description, Pre-Condition
set_widths(tc, {1: 12, 2: 38, 3: 52, 4: 95, 5: 80})
fit_rows(tc, 9, 8 + len(MODULES), 5)
cell(tc, "D5").IsTextWrapped = True
tc.Rows.getByIndex(4).Height = 6 * 520  # six lines of environment notes
fit_page_width(tc)

# ── Test Statistics ──────────────────────────────────────────────────────────
st = sheets.getByName("Test Statistics")
extra = len(MODULES) - 4  # template has data rows 12..15 inside SUM(…10:…15)
if extra > 0:
    st.Rows.insertByIndex(14, extra)
cell(st, "B6").setFormula("=$Cover.B6")
cell(st, "F6").setString(TEST_DATE)
services = sorted({m["module"] for m in MODULES})
cell(st, "B7").setString(
    f"Unit test của {len(services)} module: {', '.join(services)}. "
    f"Round 1 = mvn test ngày {TEST_DATE}.")
for i, m in enumerate(MODULES):
    r = 12 + i
    if r > 12:
        copy_row(st, 12, r, 8)
    s = f"${m['sheet']}"
    st.getCellByPosition(0, r - 1).setValue(i + 1)
    link_to_sheet(st.getCellByPosition(1, r - 1), m["sheet"])
    # Round 1 row (5) — template pointed at Round 3 (row 7)
    for col, ref in zip(range(2, 9), ["B5", "C5", "D5", "B9", "C9", "D9", "B3"]):
        st.getCellByPosition(col, r - 1).setFormula(f"={s}.{ref}")
cell(st, "B7").IsTextWrapped = True
st.Rows.getByIndex(6).Height = 2 * 520
st.Columns.getByIndex(1).Width = 60 * MM  # Function code holds the longest sheet names
fit_page_width(st)

doc.calculateAll()
doc.storeToURL(uno.systemPathToFileUrl(OUT), (prop("FilterName", "Calc MS Excel 2007 XML"),))
doc.close(True)
print("wrote", OUT)
