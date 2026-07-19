---
title: "AGRICONTRACT"
subtitle: "Phân tích thị trường và bài toán"
author: "Tài liệu đồ án tốt nghiệp"
date: "Phiên bản final · Tháng 7/2026"
toc-title: "Mục lục"
---

# **Tóm tắt điều hành**

AgriContract nhắm tới khoảng trống ở **tầng thực thi hợp đồng nông sản B2B**: chuẩn hoá thoả thuận, ký điện tử, quản lý cọc và tiền milestone qua một đơn vị giữ tiền hợp pháp, ghi nhận giao nhận/giám định, phân định trách nhiệm và tạo gói bằng chứng kiểm chứng được. Nền tảng không thay thế logistics, sàn thương mại điện tử, ngân hàng, cơ quan giải quyết tranh chấp hoặc tổ chức chứng nhận EUDR.

Bản thiết kế Phase 2 làm rõ ba điểm ảnh hưởng trực tiếp đến luận điểm thị trường. Thứ nhất, hệ thống không dùng một nút “huỷ và phạt”; rút đề nghị, chấm dứt đồng thuận, thay thế hợp đồng, vi phạm, bất khả kháng và lỗi kích hoạt là các tình huống khác nhau. Thứ hai, người yêu cầu chấm dứt không mặc nhiên là bên vi phạm; hậu quả tiền và uy tín chỉ phát sinh sau **AttributionDecision/RemedyDecision** cuối cùng. Thứ ba, tiền cọc giữa các bên và khoản tiền được tổ chức tín dụng phong toả phải được phân biệt về bản chất pháp lý và mô hình vận hành.

Các số liệu TAM/SAM/SOM, willingness-to-pay, mức cọc khởi điểm và tác động giảm “bẻ kèo” vẫn là giả thuyết cần pilot xác nhận. Vì vậy tài liệu trình bày cơ hội thị trường và logic sản phẩm, không tuyên bố AgriContract đã vận hành production, đã tích hợp ngân hàng thật hoặc đã chứng minh hiệu quả nhân quả.

# **1. Quy mô thị trường**

Năm 2025, kim ngạch xuất khẩu nông lâm thuỷ sản Việt Nam đạt 70,09 tỷ USD, tăng 12% so với năm 2024 và vượt mục tiêu 65 tỷ USD đã đề ra. Thặng dư thương mại đạt kỷ lục 21 tỷ USD. Bộ Nông nghiệp và Môi trường đặt mục tiêu 73–74 tỷ USD cho năm 2026.

| **Mặt hàng**        | **Kim ngạch 2025** | **Tăng trưởng YoY** | **Vị thế toàn cầu**              |
|---------------------|--------------------|---------------------|----------------------------------|
| Cà phê              | 8,57 tỷ USD        | +54,4%              | Xuất khẩu Robusta số 1 thế giới  |
| Rau quả             | 8,56 tỷ USD        | Kỷ lục mới          | Top 10 thế giới                  |
| Gỗ & sản phẩm gỗ    | 17,32 tỷ USD       | +6,6%               | Xuất khẩu đồ gỗ số 2 thế giới    |
| Cao su (toàn ngành) | ~11 tỷ USD         | +~10%               | Cao su tự nhiên top 3 thế giới   |
| Điều nhân           | 5,23 tỷ USD        | +20,4%              | Số 1 thế giới — 18 năm liên tiếp |

*Nguồn: Bộ Nông nghiệp và Môi trường — Họp báo tổng kết 2025 (6/1/2026); VietnamPlus — Agriculture sector on path to \$74B (23/2/2026).*

Bốn ngành hàng nằm trong phạm vi phục vụ trực tiếp của nền tảng — **cà phê, lúa gạo, cao su, điều** — được chọn vì chúng hội tụ đủ hai điều kiện: giá trị giao dịch cao và quy trình thu mua từ HTX còn phụ thuộc nặng vào niềm tin cá nhân. Cà phê và cao su thuộc phạm vi điều chỉnh của EUDR; lúa gạo và điều không thuộc EUDR nhưng chịu đúng ba đặc thù cấu trúc mô tả ở Mục 2.

## **1.1 Cà phê — Siêu chu kỳ giá và hệ quả chuỗi cung ứng**

Trong niên vụ 2024, giá cà phê Robusta tại Tây Nguyên leo từ 60 triệu đồng/tấn lên 135 triệu đồng/tấn. Biên độ tăng 125% trong chưa đầy một năm tạo ra áp lực phá vỡ hợp đồng có hệ thống trên toàn chuỗi thu mua — không phải ngoại lệ, mà là phản ứng hợp lý về mặt kinh tế khi không có cơ chế ràng buộc.

> *“Một tỷ lệ lớn các nhà cung cấp không giao hàng, nên các nhà xuất khẩu đang vật lộn.”*
>
> — Ông Phan Minh Thông, Chủ tịch Phúc Sinh Group
>
> *“Chúng tôi phải giảm kế hoạch từ 125.000 tấn xuống 105.000 tấn để kiểm soát rủi ro vốn. Với giá tăng như vậy, 100 triệu đồng trước kia mua được 2 tấn thì nay chỉ còn 1 tấn.”*
>
> — Ông Lê Đức Huy, Tổng Giám đốc Công ty XNK 2/9 Đắk Lắk

Kim ngạch xuất khẩu cà phê năm 2025 đạt 8,57 tỷ USD (+54,4%), với sản lượng 1,5 triệu tấn. Thị trường EU chiếm khoảng 40% tổng kim ngạch — đây là thị trường xuất khẩu chịu ảnh hưởng trực tiếp từ EUDR.

*Nguồn: VTV.vn — Giá thu mua tăng cao, cơ hội và thách thức của ngành cà phê Việt Nam (17/4/2024); VICOFA — Báo cáo tổng kết niên vụ cà phê 2024–2025 (10/2025).*

## **1.2 Lúa gạo — Quy mô nội địa lớn và bất đối xứng thu mua**

Lúa gạo không thuộc phạm vi điều chỉnh của EUDR, nhưng là ngành hàng chịu bất đối xứng quyền lực rõ nét nhất trong chuỗi thu mua. Tại Đồng bằng sông Cửu Long, khoảng 90% sản lượng lúa vẫn tiêu thụ qua kênh thương lái; doanh nghiệp thu mua trực tiếp từ nông dân chỉ chiếm khoảng 10%. Đặc thù thứ hai của lúa gạo là sự đa dạng giống trong cùng một thời điểm và địa bàn: nhiều giống (OM 18, ST25, IR 50404…) cùng tồn tại tại một tỉnh với mặt bằng giá khác hẳn nhau — khiến việc tham chiếu giá và xác định đúng mặt hàng trở thành yêu cầu bắt buộc chứ không phải chi tiết phụ.

Với lúa gạo, giá trị cốt lõi của nền tảng nằm ở cơ chế ký quỹ, giải quyết tranh chấp và tích luỹ uy tín — không phải ở compliance EUDR.

*Nguồn: Tạp chí Kinh tế Sài Gòn (20/6/2025).*

## **1.3 Cao su — Tăng trưởng giá trị**

Năm 2024, Việt Nam xuất khẩu xấp xỉ 2 triệu tấn cao su tự nhiên, đạt 3,42 tỷ USD (+18% về giá trị dù giảm 6% về sản lượng). Tính toàn ngành — bao gồm sản phẩm chế biến và gỗ cao su — đạt 10,2 tỷ USD. Hiệp hội Cao su Việt Nam (VRA) dự báo năm 2025 đạt 11–11,6 tỷ USD nhờ giá hồi phục và nhu cầu tăng từ Trung Quốc và Ấn Độ. Cao su thuộc phạm vi điều chỉnh của EUDR.

*Nguồn: VRA — Vietnam Rubber Industry International Conference 2025 (12/2025).*

## **1.4 Điều — Dẫn đầu xuất khẩu, phụ thuộc nguyên liệu nhập khẩu**

Năm 2025, Việt Nam xuất khẩu 766.600 tấn điều nhân, đạt 5,23 tỷ USD (+20,4%), giữ vị trí số 1 thế giới 18 năm liên tiếp với thị phần hơn 80% tổng xuất khẩu điều nhân toàn cầu. Điểm yếu cấu trúc: 90% nguyên liệu thô phụ thuộc nhập khẩu từ châu Phi và Campuchia — rủi ro đứt gãy chuỗi cung ứng là thường trực, làm tăng giá trị của các cơ chế ràng buộc giao hàng đúng cam kết.

*Nguồn: VINACAS & Tổng cục Hải quan Việt Nam (1/2026).*

## **1.5 Kỷ luật định lượng TAM/SAM/SOM — không đồng nhất kim ngạch với thị trường phần mềm**

Kim ngạch xuất khẩu 70,09 tỷ USD chứng minh quy mô và mức độ quan trọng của chuỗi nông sản, nhưng không phải TAM của AgriContract. Quy mô thị trường phần mềm phải được dựng bottom-up từ số tổ chức có khả năng mua, số hợp đồng phù hợp hoặc GMV thực sự đi qua nền tảng.

| **Lớp** | **Định nghĩa vận hành**                                                                   | **Công thức ưu tiên**                                                          | **Dữ liệu còn thiếu**                        |
|---------|-------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|----------------------------------------------|
| TAM     | Toàn bộ doanh nghiệp/HTX pháp nhân trong bốn ngành có nhu cầu quản trị hợp đồng B2B       | N tổ chức mục tiêu × phí trung bình năm; hoặc GMV hợp đồng phù hợp × take rate | Số tổ chức, số hợp đồng/năm, mức phí thực tế |
| SAM     | Phần TAM có tài khoản ngân hàng, hợp đồng điện tử và quy trình đủ chuẩn hoá để triển khai | TAM × tỷ lệ đủ điều kiện số hoá và tích hợp                                    | Tỷ lệ sẵn sàng số, quy mô hợp đồng tối thiểu |
| SOM     | Số anchor buyer/hiệp hội có thể tiếp cận và triển khai trong ba năm đầu                   | Số khách hàng khả thi × ACV đã kiểm chứng                                      | Pipeline, chu kỳ bán hàng, conversion rate   |

**Trạng thái hiện tại —** Tài liệu chưa có dữ liệu đủ để gắn một con số TAM/SAM/SOM đáng tin cậy. Công thức được chốt trước; con số chỉ được điền sau khảo sát sơ cấp và báo giá thử nghiệm, tránh lấy kim ngạch xuất khẩu nhân một tỷ lệ tuỳ ý.

# **2. Ba đặc thù cấu trúc tạo ra nhu cầu**

Các ngành hàng trên có quy mô xuất khẩu lớn nhưng cơ sở hạ tầng giao dịch B2B vẫn hoạt động theo cơ chế thủ công, phụ thuộc vào niềm tin cá nhân và chịu rủi ro tập trung cao. Ba đặc thù cấu trúc sau đây tạo ra môi trường mà chi phí phá vỡ hợp đồng thường thấp hơn lợi ích thu được — đây là gốc rễ của vấn đề, không phải triệu chứng.

## **2.1 Tính mùa vụ — phá vỡ hợp đồng là quyết định kinh tế hợp lý**

Hợp đồng nông sản thường ký trước thu hoạch 3–6 tháng. Trong khoảng thời gian đó, nếu giá thị trường tăng mạnh, bên bán có thể thu được khoản chênh lệch lớn hơn bất kỳ khoản phạt nào — đặc biệt khi hợp đồng không có cơ chế ràng buộc tài chính nào ngoài lời hứa. Đây không phải vấn đề đạo đức mà là một incentive có thật, cần được giải quyết ở tầng thiết kế cơ chế.

> *“Khi doanh nghiệp ký hợp đồng thu mua với nông dân ở mức 7.000 đồng/kg, nhưng đến thời điểm thu mua giá tăng lên 10.000 đồng/kg, doanh nghiệp chỉ trả đúng giá hợp đồng. Nông dân bẻ kèo là phản ứng tất yếu.”*
>
> — GS.TS Trần Đức Viên, nguyên Giám đốc Học viện Nông nghiệp Việt Nam

Theo đánh giá tại Diễn đàn Thúc đẩy hợp tác liên kết chuỗi giá trị nông nghiệp (tháng 8/2024), chỉ khoảng 30% liên kết đạt mức bền chắc. Phần còn lại là liên kết lỏng lẻo, không có cơ chế thực thi.

> *“Mô hình liên kết “4 nhà” đang đứt gãy vì nạn bẻ kèo. Cần lập các hợp đồng chuỗi ràng buộc trách nhiệm để ngân hàng tự tin giải ngân, thay vì chỉ chăm chăm đếm tài sản thế chấp.”*
>
> — Bà Cao Xuân Thu Vân, Chủ tịch Liên minh Hợp tác xã Việt Nam

*Nguồn: Vietnamnet — 3 chiêu để hạn chế doanh nghiệp bẻ kèo, nông dân chạy làng (30/8/2024); Báo Pháp Luật TP.HCM — Nghịch lý xuất khẩu cà phê Việt (4/4/2026).*

**Phá hợp đồng không phải giả định của nhóm — báo chí và cơ quan quản lý gọi thẳng tên (“bẻ kèo”) trong năm 2026.** Đài Tiếng nói Việt Nam (06/2026) mô tả tình trạng nông dân rũ bỏ cam kết đã ký khi thương lái đẩy giá, và nhấn mạnh rằng khi phá hợp đồng xảy ra thì gần như không tồn tại cơ chế pháp lý đủ mạnh để bảo vệ quyền lợi — đây chính là khoảng trống mà AgriContract lấp. Báo Đắk Lắk (2024) ghi nhận điệp khúc “chặt–trồng, bẻ cọc, bẻ kèo” phá vỡ liên kết ngay tại vùng cà phê Tây Nguyên, thị trường lõi của hệ thống.

**Hiện tượng xảy ra theo cả hai chiều — nền tảng cho thiết kế đối xứng.** Tạp chí Kinh tế Môi trường (VFC, 06/2026) xác nhận hợp đồng bao tiêu bị phá vỡ ở cả hai phía: khi giá thị trường vượt giá hợp đồng, một bộ phận nông dân bán ra ngoài; khi giá xuống dưới giá đã ký, một số doanh nghiệp từ chối thu mua hoặc ép giá lại. Vụ doanh nghiệp viện “bất khả kháng” không thu mua khoảng 2.000 tấn đu đủ như cam kết là ví dụ cụ thể cho nhánh doanh nghiệp phá hợp đồng. Đây là căn cứ thực tế cho cơ chế penalty/lock đối xứng của AgriContract — cả bên mua lẫn bên bán đều chịu ràng buộc, không phải chỉ bảo vệ một phía.

**Benchmark quốc tế xác nhận side-selling là rủi ro có thể đo được, nhưng không được dùng thay cho số liệu Việt Nam.** Nghiên cứu chuỗi malt barley tại Ethiopia ước tính khoảng 30% sản lượng bị side-selling; nghiên cứu trên 370 hộ cao su tại Ghana ghi nhận 20% nông hộ tham gia contract farming có side-selling. Các nghiên cứu đồng thời chỉ ra nguyên nhân không chỉ là đạo đức cá nhân mà gồm giá spot vượt giá hợp đồng, chậm thanh toán, thiết kế hợp đồng thiếu linh hoạt và giá trị quan hệ tương lai chưa đủ lớn. Đây là bằng chứng hỗ trợ hướng thiết kế incentive của AgriContract, không phải bằng chứng rằng tỷ lệ vi phạm tại Việt Nam cũng là 20–30%.

*Nguồn: Alemu, Guinan & Hermanson (2021), DOI 10.1080/09614524.2020.1860194; Ewusi Koomson et al. (2022), DOI 10.1080/14728028.2022.2079007; Macchiavello (2022), DOI 10.1146/annurev-economics-051420-110722.*

*Nguồn: VOV — Nạn bẻ kèo trong nông nghiệp (06/2026); Báo Đắk Lắk — Cái bẫy từ giá nông sản tăng đột biến (2024); Tạp chí Kinh tế Môi trường/VFC (06/2026); VnBusiness — liên kết đầu ra nông sản.*

> **Căn cứ pháp lý — Nghị định 98/2018/NĐ-CP, Điều 4 và Điều 15.** Hợp đồng liên kết sản xuất và tiêu thụ nông sản phải được lập thành văn bản (Điều 4). Các bên được lựa chọn phương thức phù hợp — thương lượng, hoà giải, hoặc trọng tài — mà không bắt buộc qua toà án (Điều 15).

## **2.2 Hàng dễ hỏng — mỗi ngày tranh chấp là thiệt hại không thu hồi được**

Khác với hàng công nghiệp, nông sản không thể chờ. Thủ tục tố tụng tại toà án thương mại Việt Nam trung bình kéo dài 1–3 năm — đủ thời gian để toàn bộ hàng hoá mất giá trị hoàn toàn. Năm 2024, Trung tâm Trọng tài Quốc tế Việt Nam (VIAC) ghi nhận 475 vụ tranh chấp thương mại, cao nhất kể từ khi thành lập, trong đó tranh chấp mua bán hàng hoá chiếm tỷ lệ lớn nhất (25%).

*Nguồn: VIAC — Thống kê hoạt động giải quyết tranh chấp năm 2024.*

> **Căn cứ pháp lý — Luật Trọng tài Thương mại 2010, Điều 5.** Các bên có quyền thoả thuận chọn trọng tài làm phương thức giải quyết tranh chấp trước hoặc sau khi tranh chấp phát sinh. Phán quyết trọng tài là chung thẩm, không bị kháng cáo — nhanh hơn toà án và phù hợp với đặc thù hàng hoá dễ hỏng.

## **2.3 Bất đối xứng quyền lực — HTX không có công cụ tự bảo vệ**

Tại Đồng bằng sông Cửu Long, 90% sản lượng lúa vẫn tiêu thụ qua kênh thương lái, doanh nghiệp thu mua trực tiếp từ nông dân chỉ chiếm khoảng 10%. Bất đối xứng thông tin và quyền lực khiến HTX nhỏ không có khả năng đàm phán thực chất, càng không có nguồn lực pháp lý để tự bảo vệ khi bị vi phạm hợp đồng. Hệ quả kéo dài sang tận khả năng tiếp cận vốn:

- 75,5% doanh nghiệp không thể tiếp cận tín dụng nếu thiếu tài sản thế chấp (VCCI — Báo cáo Kinh tế tư nhân 2025).


- Dư nợ tín dụng nông nghiệp nông thôn khoảng 4,2 triệu tỷ đồng (đầu 2026), chiếm hơn 22% tổng dư nợ nền kinh tế — quy mô lớn nhưng dòng vốn chưa chạm được doanh nghiệp vì phụ thuộc tài sản thế chấp.

- Nguyên nhân chính: thiếu tài sản thế chấp và không có lịch sử tín dụng chính thức có thể xác minh — đúng khoảng trống dữ liệu dòng tiền mà AgriContract sinh ra.

*Nguồn: VCCI — Báo cáo Kinh tế tư nhân 2025; VnEconomy, Báo Đầu Tư — khơi thông dòng vốn tín dụng nông nghiệp xanh và số (2026).*

# **3. Ba yếu tố thúc đẩy đồng thời**

Nhu cầu tự thân của ngành đã tồn tại từ lâu, nhưng ba yếu tố bên ngoài dưới đây mới là thứ biến nó thành nhu cầu cấp bách và có deadline cụ thể tại thời điểm này.

## **3.1 EUDR — Áp lực tuân thủ cứng từ thị trường EU**

Quy định EU 2023/1115 (sửa đổi bởi 2025/2650) yêu cầu toàn bộ lô hàng cà phê, cao su, gỗ, ca cao nhập khẩu vào EU phải kèm bằng chứng deforestation-free và audit trail truy xuất đến toạ độ GPS từng mảnh đất trồng, chứng minh không có phá rừng sau 31/12/2020. Deadline ràng buộc pháp lý: 30/12/2026 đối với doanh nghiệp lớn và vừa; 30/6/2027 đối với phần lớn doanh nghiệp nhỏ và siêu nhỏ. Nhóm nhỏ/siêu nhỏ đã thuộc EUTR vẫn theo mốc 30/12/2026.

| **Deadline** | **Đối tượng**                        | **Yêu cầu tối thiểu**                                                                                    |
|--------------|--------------------------------------|----------------------------------------------------------------------------------------------------------|
| 30/12/2026   | Doanh nghiệp lớn & vừa               | Due Diligence Statement đầy đủ, truy xuất đến toạ độ GPS                                                 |
| 30/6/2027    | Phần lớn doanh nghiệp nhỏ & siêu nhỏ | Nghĩa vụ bắt đầu; simplified declaration/postal address chỉ áp micro/small primary operator đủ điều kiện |

Một yêu cầu của EUDR quyết định trực tiếp độ phức tạp của bài toán truy xuất: **cấm mass balance** — không được gộp hàng từ nhiều nguồn rồi khai đại diện bằng một mảnh đất. Phải khai đủ toàn bộ mảnh đất đóng góp vào lô hàng, tách bạch rõ ràng. Điều này va trực tiếp với bản chất của HTX: một lô hàng thường được gom từ nhiều hộ thành viên, mỗi hộ có mảnh đất riêng — nên bằng chứng nguồn gốc không thể là một toạ độ đơn, mà phải là tập hợp toạ độ của tất cả các hộ đóng góp. Đây chính là tầng dữ liệu mà nền tảng phải xử lý ở khâu thu mua từ HTX.

Việt Nam được phân loại “low-risk” trong EUDR benchmarking, tạo lợi thế cạnh tranh so với các nguồn cung “high-risk”. Để tận dụng lợi thế này, doanh nghiệp cần chứng minh audit trail đầy đủ tại tầng thu mua từ HTX — chính là layer AgriContract xử lý. EUDR không bắt buộc sử dụng AgriContract; nó tạo ra yêu cầu pháp lý về audit trail số hoá, và AgriContract giải quyết một mắt xích cụ thể trong bức tranh compliance đó, không phải toàn bộ giải pháp.

*Nguồn: EC Regulation (EU) 2025/2650 — Official Journal (23/12/2025); World Resources Institute — What Is the EUDR? (cập nhật 5/2026).*

## **3.2 Quy mô xuất khẩu và áp lực chuẩn hoá**

Với 70 tỷ USD xuất khẩu năm 2025, chuẩn hoá quy trình giao dịch không còn là lựa chọn tối ưu mà là điều kiện duy trì khả năng tiếp cận thị trường quốc tế. BIDV và Techcombank đã tham gia nền tảng truy xuất blockchain Agrichain (Exabyte) với vai trò xác thực dữ liệu — tín hiệu rõ ràng rằng các định chế tài chính đang dịch chuyển từ vai trò tài trợ vốn sang vai trò xác thực thông tin trong chuỗi nông sản.

*Nguồn: Vietnam.vn — Banks partner with the digital agricultural supply chain (1/4/2026).*

## **3.3 Chính sách số hoá nông nghiệp**

Quyết định 749/QĐ-TTg (3/6/2020) xác định nông nghiệp là một trong tám lĩnh vực ưu tiên chuyển đổi số quốc gia, với mục tiêu 50% doanh nghiệp nông nghiệp ứng dụng công nghệ số vào năm 2025. Thực tế đạt được chỉ khoảng 30% — khoảng cách 20 điểm phần trăm là dư địa thị trường cụ thể, không phải ước tính.

*Nguồn: PSAV — Viet Nam prioritizes digitalization of agriculture sector; Tạp chí Kinh tế và Dự báo (28/2/2025).*

# **4. Khoảng trống thị trường**

## **4.1 Các giải pháp hiện có và giới hạn của chúng**

| **Giải pháp**              | **Năng lực**                                                     | **Giới hạn**                                                                                        | **Tình trạng**                                                |
|----------------------------|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| Hợp đồng giấy truyền thống | Có giá trị pháp lý theo BLDS 2015                                | Không có ký quỹ; không audit trail số; không tự thực thi penalty                                    | Chiếm ~70% thị trường                                         |
| Agrichain (Exabyte)        | Truy xuất nguồn gốc blockchain; BIDV và Techcombank tích hợp     | Không có đàm phán hợp đồng; không ký quỹ; không cơ chế penalty                                      | Đang hoạt động — tầng traceability                            |
| Kamereo                    | B2B procurement cho nhà hàng/retailer; Series B 7,8 triệu USD    | Không phải forward contract nông sản; không phục vụ HTX                                             | Đang hoạt động — segment khác                                 |
| Alibaba Trade Assurance    | Ký quỹ B2B quốc tế; bảo vệ hơn 160 triệu đơn; 37 triệu buyer     | Không bản địa hoá cho HTX Việt Nam; không workflow tiếng Việt; không tích hợp EUDR                  | Bằng chứng khái niệm ở quy mô lớn                             |
| Koina (đóng cửa 2024)      | Farm-to-business; VinaCapital hậu thuẫn; huy động \>1 triệu USD  | Không có contract layer hoặc ký quỹ trong phạm vi rà soát; mô hình full-stack đòi hỏi vận hành nặng | Đã ngừng — nguyên nhân công khai chưa đủ để kết luận duy nhất |
| AgriContract               | Vòng đời hợp đồng + Quản lý cọc/escrow + audit trail + hỗ trợ xử lý tranh chấp | Không giải quyết logistics — phạm vi giới hạn có chủ đích                                           | Phase 2 ở trạng thái thiết kế; chưa production                                               |

*Nguồn: Kamereo Series B (2024); Alibaba Trade Assurance.*

Nhìn theo trục “tầng giá trị” thay vì từng sản phẩm, rà soát công khai hiện tại chưa tìm thấy đối thủ tại Việt Nam phủ đồng thời ba năng lực AgriContract nhắm tới — ký quỹ số hoá, workflow thực thi điều khoản theo milestone và tranh chấp ba cấp:

| **Đối thủ**                   | **Tầng**                      | **Ký quỹ?**   | **Workflow theo điều kiện?** | **Tranh chấp 3-tier?** |
|-------------------------------|-------------------------------|---------------|------------------|------------------------|
| Kamereo / FoodMap / Koina     | Phân phối (tự mua-tự bán)     | Không         | Không            | Không                  |
| Agrichain / Intimex           | Truy xuất nguồn gốc           | Không         | Không            | Không                  |
| CeCA                          | Hợp đồng điện tử              | Không         | Không (chỉ ký)   | Không                  |
| Escrow B2C (BCT / Ngân Lượng) | Thanh toán bán lẻ             | Có (B2C)      | Không            | Không                  |
| Escrow ngân hàng (BIDV)       | Ký quỹ thủ công               | Có (thủ công) | Không            | Không                  |
| AgriContract                  | Thực thi hợp đồng B2B forward | Có (số hoá)   | Có (thiết kế milestone)   | Có (INSPECTOR 3-tier)  |

**Câu chốt:** trong phạm vi rà soát công khai hiện tại, chưa tìm thấy sản phẩm tại Việt Nam phủ đồng thời “workflow theo điều kiện + giám định nhiều cấp cho forward contract B2B”. Các nhóm hiện có thường tập trung vào phân phối, truy xuất/ký, hoặc ký quỹ B2C. Đây là khoảng trống giả thuyết AgriContract nhắm tới; kết luận cạnh tranh phải được cập nhật khi phỏng vấn khách hàng và vendor discovery phát hiện giải pháp nội bộ hoặc sản phẩm chưa công khai.

*Nguồn: Tổng hợp rà soát đối thủ trực tiếp — Kamereo, FoodMap, Koina, Agrichain/Intimex, CeCA, cổng thanh toán B2C được NHNN cấp phép, dịch vụ ký quỹ ngân hàng (2025-2026).*

## **4.2 Vì sao khoảng trống contract layer vẫn còn tồn tại**

**Bài học về phân khúc và phạm vi.** Một số startup agtech Việt Nam đi theo mô hình trực tiếp tới nông dân hoặc full-stack, nơi chi phí thu hút và vận hành có thể cao so với giá trị giao dịch của từng hộ. Trong phạm vi rà soát công khai, chưa thấy mô hình nào triển khai contract layer qua hiệp hội ngành hàng. Đây là một hướng khả thi để tận dụng trust sẵn có ở tầng HTX mà không yêu cầu startup tự xây dựng toàn bộ uy tín từ đầu, nhưng chưa phải kênh phân phối đã được xác nhận.

**Rào cản pháp lý.** Giữ tiền của người khác mà không có giấy phép trung gian thanh toán từ Ngân hàng Nhà nước là hành vi bị nghiêm cấm theo Nghị định 52/2024 — mức phạt hành chính 100–200 triệu đồng và có thể truy cứu trách nhiệm hình sự theo Nghị định 88/2019. Yêu cầu tích hợp ngân hàng ngay từ đầu là rào cản đáng kể đối với startup ở giai đoạn sớm.

> **Căn cứ pháp lý — Nghị định 52/2024/NĐ-CP, Điều 8, Khoản 7 và Nghị định 88/2019/NĐ-CP.** Nghiêm cấm cung ứng dịch vụ trung gian thanh toán khi chưa được NHNN cấp Giấy phép hoạt động. Mức phạt 100–200 triệu đồng đối với hành vi vi phạm và buộc chấm dứt hoạt động.

**Thời điểm.** Trước khi EUDR có deadline pháp lý cứng, không có áp lực bên ngoài nào buộc doanh nghiệp xuất khẩu phải số hoá tầng thu mua — Excel và hợp đồng giấy vẫn đủ để vận hành. Các mốc 30/12/2026 và 30/6/2027 biến việc chuẩn hoá dữ liệu truy xuất và due diligence đối với hàng thuộc phạm vi EUDR thành nghĩa vụ cấp thiết; việc mua AgriContract vẫn là lựa chọn thương mại cần chứng minh.

## **4.3 Bốn ngành — bốn cấu hình, một nền tảng**

Bốn ngành hàng mục tiêu không đồng nhất về yêu cầu: EUDR chỉ áp cà phê và cao su (không áp gạo và điều); nguồn giá tự động chỉ có cho cà phê/gạo (VNSAT) và cao su (quốc tế); geolocation chỉ bắt buộc cho ngành thuộc EUDR. AgriContract xử lý sự khác biệt này bằng thiết kế module hoá — bật/tắt tầng geolocation và giá tham chiếu theo commodity — thay vì ép một cấu hình chung cho mọi ngành.

| **Ngành** | **Thuộc EUDR?** | **Giá tự động?** | **Geolocation?** | **Động lực chính cho AgriContract**          |
|-----------|-----------------|------------------|------------------|----------------------------------------------|
| Cà phê    | Có — nặng nhất  | Có (VNSAT)       | Bắt buộc         | EUDR + escrow + tranh chấp — cấu hình đầy đủ |
| Gạo       | Không           | Có (VNSAT)       | Tuỳ chọn         | Escrow + tranh chấp + Đề án 1 triệu ha       |
| Cao su    | Có              | Có (quốc tế)     | Bắt buộc         | EUDR + escrow — có giá tham chiếu quốc tế    |
| Điều      | Không           | Không (Admin)    | Không            | Escrow + tranh chấp — core tối giản          |

**Ý nghĩa cho phản biện:** bốn ngành cho bốn cấu hình khác nhau chứng minh geolocation/EUDR là module bật-tắt theo luật, không phải năng lực lõi phổ quát. Điều — không EUDR, không giá tự động — là ví dụ cho thấy giá trị cốt lõi (escrow + tranh chấp + uy tín) vận hành độc lập với EUDR; cà phê là ví dụ đầy đủ tính năng. Đây là câu trả lời sẵn cho câu hỏi “vì sao đầu tư nặng vào geolocation mà chỉ phục vụ 2/4 ngành”.

*Nguồn: Tổng hợp rà soát bốn ngành hàng — phạm vi EUDR (EU 2023/1115), nguồn giá VNSAT/quốc tế, yêu cầu geolocation theo commodity (2026).*

# **5. Ba pain point hệ thống và ánh xạ giải pháp**

| **Pain point** | **Biểu hiện thực tế** | **Cơ chế Phase 2** | **Giới hạn/điều kiện** |
|---|---|---|---|
| Thiếu bằng chứng hợp đồng | Thoả thuận phân tán qua giấy, tin nhắn và file rời; khó chứng minh bản terms nào đã được hai bên chấp thuận | `ContractTerms` bất biến sau hai chữ ký cùng `signedContentHash`; OTP được binding theo user–contract–terms; audit chain append-only và gói bằng chứng | Chữ ký nền tảng chưa phải chữ ký số CA; giá trị chứng cứ vẫn do cơ quan có thẩm quyền đánh giá |
| Không có cơ chế giữ và giải ngân tiền trung lập | Trả trước, cọc và thanh toán theo đợt phụ thuộc niềm tin; dễ phát sinh trì hoãn hoặc chiếm dụng | bank-service giữ sổ cái tiền tệ duy nhất; escrow-service chỉ chiếu trạng thái; mọi leg tiền cuối cùng đi từ `remedy.finalized`; terminal chỉ khi đủ leg và tổng tiền lock bằng 0 | Ngân hàng thật là tích hợp ngoài phạm vi đồ án; mock ledger không được diễn giải là giấy phép giữ tiền |
| Không có phân định trách nhiệm đáng tin cậy | Người bấm nút chấm dứt dễ bị đánh đồng với bên vi phạm; allegation có thể gây phạt oan | Tách `requestedBy`, `allegedBreachingRole`, `finalBreachingRole`; Rổ A tự động khi có bằng chứng khách quan, Rổ B qua `BreachCase`; uy tín tiêu cực chỉ từ quyết định cuối cùng | Phase 2 không thay thế toà án/trọng tài; attribution của trường hợp chủ quan vẫn cần quy trình review/giám định |
| Thiếu dữ liệu đối tác có kiểm chứng | Lịch sử hoàn thành, tranh chấp, giao hàng và thanh toán không được tổng hợp nhất quán | Lưu immutable facts và tính reputation tại thời điểm truy vấn; analytics tách loại chấm dứt, người yêu cầu và bên vi phạm cuối cùng | Dữ liệu tín dụng/AML chỉ là tín hiệu tham khảo; không tự động cấp tín dụng hoặc chặn settlement |

# **6. Tầm nhìn dài hạn — hạ tầng dữ liệu cho tín dụng nông nghiệp**

Dữ liệu hợp đồng, milestone, thanh toán, giám định và lịch sử hoàn thành có thể tạo nền cho đánh giá dòng tiền trong tương lai. Phase 2 mới thiết kế lớp sự kiện, audit và read model cần thiết; chưa có mô hình chấm điểm tín dụng được ngân hàng phê duyệt, chưa có quyết định cấp tín dụng tự động và không chuyển dữ liệu cho đối tác tín dụng khi thiếu căn cứ pháp lý/đồng ý phù hợp.

Giá trị gần hạn là giảm chi phí đối chiếu và làm rõ bằng chứng. Giá trị dài hạn — tín dụng dựa trên dòng tiền, bảo hiểm, benchmark ngành — phải được coi là hướng mở rộng có điều kiện, không phải capability đã hoàn thành.

# **7. Khung pháp lý tham chiếu**

| **Chủ đề** | **Khung tham chiếu trong tài liệu** | **Cách Phase 2 phản ánh** |
|---|---|---|
| Hợp đồng điện tử | Luật Giao dịch điện tử 2023: thông điệp dữ liệu không bị phủ nhận chỉ vì ở dạng điện tử; giá trị chứng cứ phụ thuộc độ tin cậy của cách khởi tạo, gửi, nhận, lưu trữ | Lưu bản terms bất biến, cùng content hash cho hai chữ ký, timestamp, audit record và provenance |
| Đặt cọc giữa các bên | BLDS 2015 Điều 328 | `buyerDepositRate`/`sellerDepositRate` là tham số hợp đồng; bản chất và hậu quả cụ thể phải được luật sư xác nhận cho mẫu hợp đồng pilot |
| Ký quỹ/tài khoản phong toả tại tổ chức tín dụng | BLDS 2015 Điều 330 và pháp luật ngân hàng/thanh toán liên quan | AgriContract không tự nhận giữ tiền thật; bank-service là adapter/ledger mô phỏng ranh giới tích hợp với custodian hợp pháp |
| Phạt vi phạm và bồi thường | Luật Thương mại 2005 Điều 300, 301, 302; BLDS và điều khoản hợp đồng áp dụng | `LegalProfile` đóng băng governing law, mức trần và `damagesPolicy`; penalty tính trên phần nghĩa vụ bị vi phạm; không double recovery |
| Bất khả kháng/miễn trách | BLDS 2015 và điều khoản hợp đồng | Có flow claim–review riêng; kết quả no-fault có `finalBreachingRole = null`, không phạt tiền/uy tín |
| Tranh chấp | Nghị định 98/2018, Luật Trọng tài thương mại 2010 và thoả thuận các bên | Hệ thống hỗ trợ review, giám định và đóng gói bằng chứng; không tự nhận là cơ quan tài phán |

**Ranh giới bắt buộc khi bảo vệ đồ án:** thiết kế pháp lý là framing kỹ thuật–nghiệp vụ, không phải legal opinion. Việc phân loại cọc, điều khoản phạt/bồi thường, mẫu hợp đồng và mô hình ngân hàng thật phải được tư vấn pháp lý xác nhận trước pilot có tiền thật.

# **8. Giới hạn của phân tích và phạm vi**

- Phase 2 đang ở trạng thái **DESIGNED — chưa code/production**. Các bảng mô tả target contract, không phải bằng chứng hệ thống đã vận hành.
- Bank integration, chuyển tiền thật, quy trình đối soát với tổ chức tín dụng và tuân thủ giấy phép nằm ngoài phạm vi implementation hiện tại.
- External inspection, email intake, OpenTimestamps, nguồn giá và baseline địa lý có điểm tích hợp/mô phỏng; độ tin cậy production phải được kiểm chứng riêng.
- Các control “zero-trust-oriented” chỉ áp tại một số trust boundary (Gateway, external verifier, identity header, fail-closed). Hệ thống không claim full Zero Trust.
- Geolocation/baseline rừng hỗ trợ EUDR evidence, không tự tạo due-diligence statement và không chứng minh tuyệt đối tính đúng của khai báo.
- Mức cọc, cửa sổ xác nhận, release floor, penalty rate trong giới hạn pháp luật, pricing và payer là tham số pilot; chưa phải tối ưu đã được chứng minh.
- Reputation/analytics là projection từ fact cuối cùng. Tín hiệu AML hoặc elevated risk Phase 2 không tự động hold tiền và không thay thế thẩm định con người.
- Không có causal evidence tại Việt Nam để tuyên bố mức giảm vi phạm; cần baseline, cohort và đo trước–sau.

## **8.1 Gói validation sơ cấp trước product–market fit**

Pilot cần xác nhận tối thiểu: (1) anchor buyer và HTX có chấp nhận hai chữ ký cùng terms; (2) mức cọc và lịch milestone có khả thi theo mùa vụ; (3) tỷ lệ tranh chấp Rổ A/Rổ B; (4) thời gian review/giám định; (5) ngân hàng/custodian chấp nhận command–reconciliation contract; (6) willingness-to-pay; và (7) tính dùng được của gói bằng chứng khi có tranh chấp thật.

# **9. Danh mục nguồn tham khảo**

### **Chính phủ & tổ chức quốc tế**

- Bộ Nông nghiệp và Môi trường — Tổng kết xuất khẩu 2025 (6/1/2026).

- EC Regulation (EU) 2025/2650 — Official Journal (23/12/2025).

- VIAC — Thống kê hoạt động giải quyết tranh chấp năm 2024.

- World Resources Institute — What Is the EUDR? (5/2026).

- We-Fi / OCB — SME financing gap Vietnam (2022).

- PSAV — Vietnam prioritizes digitalization of agriculture (Quyết định 749/QĐ-TTg).

### **Hiệp hội ngành hàng**

- VICOFA — Báo cáo tổng kết niên vụ cà phê 2024–2025 (10/2025).

- VRA — Vietnam Rubber Industry International Conference 2025 (12/2025).

- VINACAS & Tổng cục Hải quan — Xuất khẩu điều 2025 (1/2026).

### **Báo chí & chuyên gia**

- VTV.vn — Giá thu mua tăng cao, cơ hội và thách thức ngành cà phê Việt Nam (17/4/2024).

- Vietnamnet — 3 chiêu để hạn chế doanh nghiệp bẻ kèo, nông dân chạy làng (30/8/2024).

- Báo Pháp Luật TP.HCM — Nghịch lý xuất khẩu cà phê Việt (4/4/2026).

- Vietnam.vn — Banks partner with the digital agricultural supply chain (1/4/2026).

- Tạp chí Kinh tế Sài Gòn (20/6/2025).

- Tạp chí Kinh tế và Dự báo — Chuyển đổi số nông nghiệp (28/2/2025).

### **Nghiên cứu học thuật quốc tế**

- Alemu, D., Guinan, A. & Hermanson, J. (2021). Contract farming, cooperatives and challenges of side selling: malt barley value-chain development in Ethiopia. Development in Practice, 31(4), 496–510. DOI 10.1080/09614524.2020.1860194.

- Macchiavello, R. (2022). Relational Contracts and Development. Annual Review of Economics, 14. DOI 10.1146/annurev-economics-051420-110722.

- Ewusi Koomson, J. et al. (2022). Contract farming scheme for rubber production in Western Region of Ghana: Why do farmers side sell? Forests, Trees and Livelihoods. DOI 10.1080/14728028.2022.2079007.

- Tefera, D. A. & Bijman, J. (2021). Economics of contracts in African food systems: evidence from the malt barley sector in Ethiopia. Agricultural and Food Economics, 9:26. DOI 10.1186/s40100-021-00198-0.

- Abreham, G. et al. (2025). Nexus between market access and contract farming: a systematic review and meta-regression analysis. Cogent Food & Agriculture. DOI 10.1080/23311932.2025.2551263.

### **Văn bản pháp luật**

- Luật Giao dịch Điện tử 2023 (Luật 20/2023/QH15, hiệu lực 1/7/2024).

- Nghị định 52/2024/NĐ-CP — Thanh toán không dùng tiền mặt (hiệu lực 1/7/2024).

- Nghị định 98/2018/NĐ-CP — Liên kết sản xuất và tiêu thụ nông sản.

- Luật Trọng tài Thương mại 2010 (Luật 54/2010/QH12).

- Bộ luật Dân sự 2015 — Điều 328, 330, 142, 403, 156, 351.

- Luật Thương mại 2005 — Điều 300, 302.

- Nghị định 88/2019/NĐ-CP — Xử phạt vi phạm hành chính lĩnh vực tiền tệ.


