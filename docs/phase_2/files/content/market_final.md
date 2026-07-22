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

Theo Bộ Nông nghiệp và Môi trường, kim ngạch xuất khẩu nông, lâm, thuỷ sản Việt Nam năm 2025 đạt khoảng 70,09 tỷ USD. Con số này cho thấy quy mô chuỗi nông sản, nhưng không tự suy ra TAM hoặc doanh thu phần mềm. Mục tiêu xuất khẩu năm 2026 được báo cáo ở mức 73–74 tỷ USD. [23], [24]

| **Mặt hàng**        | **Kim ngạch 2025** | **Tăng trưởng YoY** | **Vị thế toàn cầu**              |
|---------------------|--------------------|---------------------|----------------------------------|
| Cà phê              | 8,57 tỷ USD        | Số liệu Chính phủ công bố cho năm 2025 | 1,5 triệu tấn [24] |
| Điều nhân           | 5,19 tỷ USD        | Số liệu Chính phủ công bố cho năm 2025 | Số liệu được dùng nhất quán trong bộ tài liệu [24] |
| Lúa gạo             | Chưa chốt trong corpus này | Giữ trong phạm vi sản phẩm | Cần nguồn thống kê riêng trước khi nêu số liệu |
| Cao su              | Chưa chốt trong corpus này | Thuộc nhóm hàng EUDR | Cần nguồn thống kê riêng trước khi nêu số liệu [30] |

Bốn ngành hàng nằm trong phạm vi phục vụ trực tiếp của nền tảng — **cà phê, lúa gạo, cao su, điều** — được chọn vì chúng hội tụ đủ hai điều kiện: giá trị giao dịch cao và quy trình thu mua từ HTX còn phụ thuộc nặng vào niềm tin cá nhân. Cà phê và cao su thuộc phạm vi điều chỉnh của EUDR; lúa gạo và điều không thuộc EUDR nhưng chịu đúng ba đặc thù cấu trúc mô tả ở Mục 2.

## **1.1 Cà phê — Siêu chu kỳ giá và hệ quả chuỗi cung ứng**

Trong năm 2024, các nguồn báo chí và báo cáo ngành ghi nhận giá cà phê Việt Nam tăng mạnh, nông dân có xu hướng giữ hàng và một số hợp đồng liên kết bị đứt gãy. Đây là bằng chứng về một episode và cơ chế incentive trong bối cảnh giá tăng, không phải thống kê cho phép gọi vi phạm là “có hệ thống” trên toàn ngành. [35], [36], [39]

Kim ngạch xuất khẩu cà phê năm 2025 được Chính phủ báo cáo ở mức 8,57 tỷ USD với sản lượng 1,5 triệu tấn. EUDR áp dụng cho cà phê, nhưng tỷ trọng EU cụ thể phải được kiểm tra theo từng năm và nguồn trước khi sử dụng. [24], [30]

## **1.2 Lúa gạo — Quy mô nội địa lớn và bất đối xứng thu mua**

Lúa gạo không thuộc phạm vi EUDR. Trong một nghiên cứu trên 280 hộ trồng gạo đặc sản ở Đồng bằng sông Hồng, collectors/wholesalers là kênh tiêu thụ phổ biến; 86,6% và 83,8% hộ lần lượt nêu láng giềng và thương lái là nguồn thông tin thường dùng. Kết quả này hỗ trợ cơ chế bất đối xứng thông tin trong một bối cảnh cụ thể, không đủ để suy ra tỷ lệ kênh thu mua 90/10 cho toàn quốc. [13]

Với lúa gạo, giá trị cốt lõi của nền tảng nằm ở cơ chế ký quỹ, giải quyết tranh chấp và tích luỹ uy tín — không phải ở compliance EUDR.

## **1.3 Cao su — Tăng trưởng giá trị**

Cao su thuộc nhóm hàng liên quan của EUDR. Corpus bằng chứng hiện tại chưa chốt lại các số liệu kim ngạch chi tiết đang có trong bản cũ; vì vậy luận điểm ở đây chỉ dùng phạm vi tuân thủ và nhu cầu dữ liệu geolocation, không dùng quy mô xuất khẩu chưa được kiểm chứng lại. [30]

## **1.4 Điều — Dẫn đầu xuất khẩu, phụ thuộc nguyên liệu nhập khẩu**

Báo cáo Chính phủ ghi kim ngạch xuất khẩu điều năm 2025 ở mức 5,19 tỷ USD. Con số này hỗ trợ nhận định rằng đây là ngành hàng có quy mô giao dịch lớn; nó không tự chứng minh prevalence của vi phạm hợp đồng hoặc nhu cầu mua phần mềm. [24]

## **1.5 Kỷ luật định lượng TAM/SAM/SOM — không đồng nhất kim ngạch với thị trường phần mềm**

Kim ngạch xuất khẩu 70,09 tỷ USD chứng minh quy mô và mức độ quan trọng của chuỗi nông sản, nhưng không phải TAM của AgriContract. Quy mô thị trường phần mềm phải được dựng bottom-up từ số tổ chức có khả năng mua, số hợp đồng phù hợp hoặc GMV thực sự đi qua nền tảng.

| **Lớp** | **Định nghĩa vận hành**                                                                   | **Công thức ưu tiên**                                                          | **Dữ liệu còn thiếu**                        |
|---------|-------------------------------------------------------------------------------------------|--------------------------------------------------------------------------------|----------------------------------------------|
| TAM     | Toàn bộ doanh nghiệp/HTX pháp nhân trong bốn ngành có nhu cầu quản trị hợp đồng B2B       | N tổ chức mục tiêu × phí trung bình năm; hoặc GMV hợp đồng phù hợp × take rate | Số tổ chức, số hợp đồng/năm, mức phí thực tế |
| SAM     | Phần TAM có tài khoản ngân hàng, hợp đồng điện tử và quy trình đủ chuẩn hoá để triển khai | TAM × tỷ lệ đủ điều kiện số hoá và tích hợp                                    | Tỷ lệ sẵn sàng số, quy mô hợp đồng tối thiểu |
| SOM     | Số anchor buyer/hiệp hội có thể tiếp cận và triển khai trong ba năm đầu                   | Số khách hàng khả thi × ACV đã kiểm chứng                                      | Pipeline, chu kỳ bán hàng, conversion rate   |

**Trạng thái hiện tại —** Tài liệu chưa có dữ liệu đủ để gắn một con số TAM/SAM/SOM đáng tin cậy. Công thức được chốt trước; con số chỉ được điền sau khảo sát sơ cấp và báo giá thử nghiệm, tránh lấy kim ngạch xuất khẩu nhân một tỷ lệ tuỳ ý.

# **2. Ba đặc thù cấu trúc tạo ra nhu cầu**

Các nghiên cứu cho thấy contract farming có thể tạo lợi ích nhưng kết quả không đồng nhất; side-selling/default có liên hệ với incentive, chênh lệch giá, chậm thanh toán và quyền định giá. Mức độ của các cơ chế này trong bốn ngành hàng tại Việt Nam cần được đo riêng. [1], [4], [6]–[8]

## **2.1 Tính mùa vụ — phá vỡ hợp đồng là quyết định kinh tế hợp lý**

Contract farming được thoả thuận trước khi sản xuất bắt đầu; thời lượng cụ thể phụ thuộc commodity và hợp đồng, nên corpus hiện tại không giữ con số phổ quát 3–6 tháng. Khi giá ngoài hợp đồng tăng hoặc thanh toán bị chậm, incentive tuân thủ có thể suy giảm; đây là mechanism claim, không phải kết luận rằng mọi hợp đồng đều vỡ. [1], [6]–[8]

Chưa có dữ liệu đại diện đủ mạnh để khẳng định tỷ lệ liên kết bền chắc trên toàn quốc. Bằng chứng hiện có hỗ trợ nhận định hẹp hơn: cả farmer và processor đều có thể reneging khi incentive và quyền định giá không phù hợp. [4]

**Vi phạm có thể phát sinh từ cả hai phía.** Thí nghiệm double moral hazard cho thấy cả farmer và processor đều có cơ hội reneging; tại Việt Nam, các báo cáo cà phê và vụ đu đủ chỉ cung cấp case evidence rằng đứt gãy cam kết có thể xảy ra ở cả nhánh bán ngoài và không thu mua. Các case này không được dùng làm prevalence statistic. [4], [36]–[38]

**Benchmark quốc tế xác nhận side-selling là rủi ro có thể đo được, nhưng không được dùng thay cho số liệu Việt Nam.** Nghiên cứu chuỗi malt barley tại Ethiopia ước tính khoảng 30% sản lượng bị side-selling; nghiên cứu trên 370 hộ cao su tại Ghana ghi nhận 20% nông hộ tham gia contract farming có side-selling. Các nghiên cứu đồng thời chỉ ra nguyên nhân không chỉ là đạo đức cá nhân mà gồm giá spot vượt giá hợp đồng, chậm thanh toán, thiết kế hợp đồng thiếu linh hoạt và giá trị quan hệ tương lai chưa đủ lớn. Đây là bằng chứng hỗ trợ hướng thiết kế incentive của AgriContract, không phải bằng chứng rằng tỷ lệ vi phạm tại Việt Nam cũng là 20–30%. [5], [6]

> **Căn cứ pháp lý — Nghị định 98/2018/NĐ-CP.** Nghị định ghi nhận hợp đồng liên kết nông nghiệp, nghĩa vụ thực hiện cam kết và các phương thức giải quyết tranh chấp theo pháp luật. [26]

## **2.2 Hàng dễ hỏng — mỗi ngày tranh chấp là thiệt hại không thu hồi được**

Tính dễ hỏng làm tăng chi phí của trì hoãn trong các tranh chấp về vận chuyển, chất lượng và thanh toán; mức độ cụ thể phụ thuộc commodity và cần được đo trong pilot. Corpus chưa có thống kê chính thức đủ mạnh về thời lượng xử lý tại tòa án. VIAC ghi nhận 475 vụ trong caseload năm 2024 và tranh chấp mua bán hàng hoá chiếm 25%; đây không phải tổng số tranh chấp của Việt Nam hoặc riêng nông nghiệp. [25], [42]

> **Căn cứ pháp lý — Luật Trọng tài Thương mại 2010, Điều 5.** Các bên có quyền thoả thuận chọn trọng tài làm phương thức giải quyết tranh chấp trước hoặc sau khi tranh chấp phát sinh. Phán quyết trọng tài là chung thẩm, không bị kháng cáo — nhanh hơn toà án và phù hợp với đặc thù hàng hoá dễ hỏng.

## **2.3 Bất đối xứng quyền lực — HTX không có công cụ tự bảo vệ**

Nghiên cứu gạo đặc sản ở Đồng bằng sông Hồng cho thấy farmers thường nhận thông tin qua láng giềng và thương lái, trong khi collectors/wholesalers là kênh phổ biến. Kết quả này hỗ trợ rủi ro bất đối xứng thông tin trong một sample cụ thể; khả năng đàm phán và nguồn lực pháp lý của HTX nhỏ vẫn phải được khảo sát riêng. [13]

- 75,5% doanh nghiệp trong khảo sát được VCCI dẫn lại cho biết không thể vay nếu thiếu tài sản thế chấp; đây là bối cảnh doanh nghiệp rộng, không phải ước lượng riêng cho HTX nông nghiệp. [33]


- Dư nợ tín dụng nông nghiệp nông thôn khoảng 4,2 triệu tỷ đồng (đầu 2026), chiếm hơn 22% tổng dư nợ nền kinh tế — quy mô lớn nhưng dòng vốn chưa chạm được doanh nghiệp vì phụ thuộc tài sản thế chấp.

- Thiếu tài sản thế chấp là một rào cản được ghi nhận; tuy nhiên transaction history chỉ là nguồn dữ liệu bổ sung tiềm năng, chưa được chứng minh có thể thay thế collateral trong quyết định cấp tín dụng. [17], [33], [34]

# **3. Ba yếu tố thúc đẩy đồng thời**

Nhu cầu tự thân của ngành đã tồn tại từ lâu, nhưng ba yếu tố bên ngoài dưới đây mới là thứ biến nó thành nhu cầu cấp bách và có deadline cụ thể tại thời điểm này.

## **3.1 EUDR — Áp lực tuân thủ cứng từ thị trường EU**

Quy định EU 2023/1115 áp dụng cho cà phê và cao su trong các commodity liên quan, yêu cầu dữ liệu nguồn và geolocation của các plot đóng góp. Mốc áp dụng thông thường là 30/12/2026; một số micro/small primary operators áp dụng từ 30/6/2027. [30]

| **Deadline** | **Đối tượng**                        | **Yêu cầu tối thiểu**                                                                                    |
|--------------|--------------------------------------|----------------------------------------------------------------------------------------------------------|
| 30/12/2026   | Doanh nghiệp lớn & vừa               | Due Diligence Statement đầy đủ, truy xuất đến toạ độ GPS                                                 |
| 30/6/2027    | Phần lớn doanh nghiệp nhỏ & siêu nhỏ | Nghĩa vụ bắt đầu; simplified declaration/postal address chỉ áp micro/small primary operator đủ điều kiện |

Một yêu cầu của EUDR quyết định trực tiếp độ phức tạp của bài toán truy xuất: **cấm mass balance** — không được gộp hàng từ nhiều nguồn rồi khai đại diện bằng một mảnh đất. Phải khai đủ toàn bộ mảnh đất đóng góp vào lô hàng, tách bạch rõ ràng. Điều này va trực tiếp với bản chất của HTX: một lô hàng thường được gom từ nhiều hộ thành viên, mỗi hộ có mảnh đất riêng — nên bằng chứng nguồn gốc không thể là một toạ độ đơn, mà phải là tập hợp toạ độ của tất cả các hộ đóng góp. Đây chính là tầng dữ liệu mà nền tảng phải xử lý ở khâu thu mua từ HTX.

Việt Nam được liệt kê trong nhóm low-risk theo danh sách 2025, nhưng phân loại này không loại bỏ các nghĩa vụ dữ liệu áp dụng. AgriContract chỉ hỗ trợ thu thập và truy hồi một phần evidence; nó không tự tạo due-diligence statement hoặc bảo đảm market access. [30], [31]

## **3.2 Quy mô xuất khẩu và áp lực chuẩn hoá**

Quy mô xuất khẩu là bối cảnh cho nhu cầu chuẩn hoá dữ liệu, không chứng minh rằng ngân hàng sẽ chuyển từ collateral sang vai trò “validator”. Transaction history có thể cung cấp thêm dữ liệu cho đánh giá dòng tiền; utility và tác động đến quyết định tín dụng phải được kiểm chứng với ngân hàng. [17], [33]

## **3.3 Chính sách số hoá nông nghiệp**

Chính sách quốc gia xác định nông nghiệp là lĩnh vực ưu tiên chuyển đổi số và đặt các mục tiêu về kinh tế số, tài khoản thương mại điện tử và số hoá dữ liệu hộ. Một khảo sát 455 hộ ở Quảng Trị cho thấy adoption phân hoá theo giáo dục, quy mô, kinh nghiệm số, trao đổi thông tin và chi phí internet. Corpus không hỗ trợ một tỷ lệ adoption quốc gia hoặc một “khoảng trống” định lượng. [14], [40], [41]

# **4. Khoảng trống thị trường**

## **4.1 Các giải pháp hiện có và giới hạn của chúng**

| **Giải pháp**              | **Năng lực**                                                     | **Giới hạn**                                                                                        | **Tình trạng**                                                |
|----------------------------|------------------------------------------------------------------|-----------------------------------------------------------------------------------------------------|---------------------------------------------------------------|
| Hợp đồng giấy truyền thống | Có thể ghi nhận cam kết theo pháp luật hợp đồng | Không mặc nhiên có audit trail số hoặc workflow milestone | Tỷ lệ paper contract chưa được xác minh |
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

**Rào cản pháp lý.** Khung thanh toán không dùng tiền mặt và chế tài cấp phép phải được đánh giá theo hoạt động thực tế, quyền kiểm soát tiền và vai trò của custodian. Từ ngày 09/02/2026, NĐ340/2025 quy định phạt 150–250 triệu đồng đối với hành vi cung ứng dịch vụ trung gian thanh toán không có giấy phép. Không được đồng nhất mọi mô hình escrow/custody với hành vi này khi chưa có legal opinion cho wire flow cụ thể. [28], [29]

> **Căn cứ pháp lý — NĐ52/2024/NĐ-CP và NĐ340/2025/NĐ-CP.** Tài liệu chỉ dùng các văn bản này để xác định licensing boundary; kết luận mô hình AgriContract có thuộc dịch vụ trung gian thanh toán hay không phải dựa trên legal review của implementation thực tế. [28], [29]

**Thời điểm.** Trước khi EUDR có deadline pháp lý cứng, không có áp lực bên ngoài nào buộc doanh nghiệp xuất khẩu phải số hoá tầng thu mua — Excel và hợp đồng giấy vẫn đủ để vận hành. Các mốc 30/12/2026 và 30/6/2027 biến việc chuẩn hoá dữ liệu truy xuất và due diligence đối với hàng thuộc phạm vi EUDR thành nghĩa vụ cấp thiết; việc mua AgriContract vẫn là lựa chọn thương mại cần chứng minh.

## **4.3 Bốn ngành — bốn cấu hình, một nền tảng**

Bốn ngành hàng mục tiêu không đồng nhất về yêu cầu: EUDR chỉ áp cà phê và cao su (không áp gạo và điều); nguồn giá tự động chỉ có cho cà phê/gạo (VNSAT); cao su có nguồn quốc tế nhưng Phase 2 vẫn nhập tay để tránh chuẩn hoá đơn vị/tỷ giá ngoài phạm vi; geolocation chỉ bắt buộc cho ngành thuộc EUDR. AgriContract xử lý sự khác biệt này bằng thiết kế module hoá — bật/tắt tầng geolocation và giá tham chiếu theo commodity — thay vì ép một cấu hình chung cho mọi ngành.

| **Ngành** | **Thuộc EUDR?** | **Giá tự động?** | **Geolocation?** | **Động lực chính cho AgriContract**          |
|-----------|-----------------|------------------|------------------|----------------------------------------------|
| Cà phê    | Có — nặng nhất  | Có (VNSAT)       | Bắt buộc         | EUDR + escrow + tranh chấp — cấu hình đầy đủ |
| Gạo       | Không           | Có (VNSAT)       | Tuỳ chọn         | Escrow + tranh chấp + Đề án 1 triệu ha       |
| Cao su    | Có              | Không — Admin nhập tay | Bắt buộc         | EUDR + escrow — có giá tham chiếu quốc tế    |
| Điều      | Không           | Không (Admin)    | Không            | Escrow + tranh chấp — core tối giản          |

**Ý nghĩa cho phản biện:** bốn ngành cho bốn cấu hình khác nhau chứng minh geolocation/EUDR là module bật-tắt theo luật, không phải năng lực lõi phổ quát. Điều — không EUDR, không giá tự động — là ví dụ cho thấy giá trị cốt lõi (escrow + tranh chấp + uy tín) vận hành độc lập với EUDR; cà phê là ví dụ đầy đủ tính năng. Đây là câu trả lời sẵn cho câu hỏi “vì sao đầu tư nặng vào geolocation mà chỉ phục vụ 2/4 ngành”.

*Nguồn: Tổng hợp rà soát bốn ngành hàng — phạm vi EUDR (EU 2023/1115), nguồn giá VNSAT/quốc tế, yêu cầu geolocation theo commodity (2026).*

# **5. Ba pain point hệ thống và ánh xạ giải pháp**

| **Pain point** | **Biểu hiện thực tế** | **Cơ chế Phase 2** | **Giới hạn/điều kiện** |
|---|---|---|---|
| Thiếu bằng chứng hợp đồng | Thoả thuận phân tán qua giấy, tin nhắn và file rời; khó chứng minh bản terms nào đã được hai bên chấp thuận | `ContractTerms` bất biến sau hai chữ ký cùng `signedContentHash`; OTP được binding theo user–contract–terms; audit chain append-only và gói bằng chứng | Chữ ký nền tảng chưa phải chữ ký số CA; giá trị chứng cứ vẫn do cơ quan có thẩm quyền đánh giá |
| Không có cơ chế giữ và giải ngân tiền trung lập | Trả trước, cọc và thanh toán theo đợt phụ thuộc niềm tin; dễ phát sinh trì hoãn hoặc chiếm dụng | bank-service giữ sổ cái tiền tệ duy nhất; escrow-service chỉ chiếu trạng thái; mọi leg tiền cuối cùng đi từ `remedy.finalized`; terminal chỉ khi đủ leg và tổng tiền lock bằng 0 | Ngân hàng thật là tích hợp ngoài phạm vi đồ án; mock ledger không được diễn giải là giấy phép giữ tiền |
| Không có phân định trách nhiệm đáng tin cậy | Người bấm nút chấm dứt dễ bị đánh đồng với bên vi phạm; allegation có thể gây phạt oan | Tách `requestedBy`, `allegedBreachingRole`, `finalBreachingRole`; Rổ A tự động khi có bằng chứng khách quan, Rổ B qua `BreachCase`; uy tín tiêu cực chỉ từ quyết định cuối cùng | Phase 2 không thay thế toà án/trọng tài; attribution của trường hợp chủ quan vẫn cần quy trình review/giám định |
| Thiếu dữ liệu đối tác có kiểm chứng | Lịch sử hoàn thành, tranh chấp, giao hàng và thanh toán có thể bị phân tán | Lưu immutable facts theo nghĩa invariant thiết kế và tính reputation tại thời điểm truy vấn; analytics tách loại chấm dứt, người yêu cầu và bên vi phạm cuối cùng | Reputation chỉ là tín hiệu từ fact đã ghi nhận và có thể chịu bias/dispute; dữ liệu tín dụng/AML không tự động cấp tín dụng hoặc chặn settlement [18], [20] |

# **6. Tầm nhìn dài hạn — hạ tầng dữ liệu cho tín dụng nông nghiệp**

Dữ liệu hợp đồng, milestone, thanh toán, giám định và lịch sử hoàn thành có thể là input bổ sung cho đánh giá dòng tiền. Bằng chứng hiện có chưa cho phép khẳng định transaction history tự động cải thiện quyết định cấp tín dụng tại Việt Nam. Phase 2 chỉ thiết kế lớp event, audit và read model; chưa có credit model được ngân hàng phê duyệt và không chuyển dữ liệu khi thiếu căn cứ pháp lý/đồng ý phù hợp. [17], [33]

Giá trị gần hạn là giảm chi phí đối chiếu và làm rõ bằng chứng. Giá trị dài hạn — tín dụng dựa trên dòng tiền, bảo hiểm, benchmark ngành — phải được coi là hướng mở rộng có điều kiện, không phải capability đã hoàn thành.

# **7. Khung pháp lý tham chiếu**

| **Chủ đề** | **Khung tham chiếu trong tài liệu** | **Cách Phase 2 phản ánh** |
|---|---|---|
| Hợp đồng điện tử | Luật Giao dịch điện tử 2023: thông điệp dữ liệu không bị phủ nhận chỉ vì ở dạng điện tử; giá trị chứng cứ phụ thuộc độ tin cậy của cách khởi tạo, gửi, nhận, lưu trữ [27] | Lưu bản terms bất biến theo invariant thiết kế, cùng content hash cho hai chữ ký, timestamp, audit record và provenance; không tự bảo đảm nội dung đầu vào đúng |
| Đặt cọc giữa các bên | BLDS 2015 Điều 328 [45] | `buyerDepositRate`/`sellerDepositRate` là tham số hợp đồng; bản chất và hậu quả cụ thể phải được luật sư xác nhận cho mẫu hợp đồng pilot |
| Ký quỹ/tài khoản phong toả tại tổ chức tín dụng | BLDS 2015 Điều 330 và pháp luật ngân hàng/thanh toán liên quan | AgriContract không tự nhận giữ tiền thật; bank-service là adapter/ledger mô phỏng ranh giới tích hợp với custodian hợp pháp |
| Phạt vi phạm và bồi thường | Luật Thương mại 2005 Điều 300, 301, 302; BLDS và điều khoản hợp đồng áp dụng [45], [46] | `LegalProfile` đóng băng governing law, mức trần và `damagesPolicy`; penalty tính trên phần nghĩa vụ bị vi phạm; không double recovery |
| Bất khả kháng/miễn trách | BLDS 2015 và điều khoản hợp đồng | Có flow claim–review riêng; kết quả no-fault có `finalBreachingRole = null`, không phạt tiền/uy tín |
| Tranh chấp | Nghị định 98/2018, Luật Trọng tài thương mại 2010 và thoả thuận các bên [26], [47] | Hệ thống hỗ trợ review, giám định và đóng gói bằng chứng; không tự nhận là cơ quan tài phán |

**Ranh giới bắt buộc khi bảo vệ đồ án:** thiết kế pháp lý là framing kỹ thuật–nghiệp vụ, không phải legal opinion. Việc phân loại cọc, điều khoản phạt/bồi thường, mẫu hợp đồng và mô hình ngân hàng thật phải được tư vấn pháp lý xác nhận trước pilot có tiền thật.

# **8. Current Scope**

Phân tích này tập trung vào bốn commodity (cà phê, gạo, cao su, điều), nhu cầu contract layer B2B, mô hình phân phối qua anchor buyer/hiệp hội, các áp lực EUDR và tuân thủ, cùng cách các pain point thị trường ánh xạ vào workflow milestone, custody, inspection, attribution, audit và evidence. Các ước lượng TAM/SAM/SOM được dùng như khung định hướng; không đồng nhất kim ngạch nông sản với doanh thu phần mềm.

# **9. Known Limitations**

- Phase 2 đang ở trạng thái **DESIGNED — chưa code/production**. Các bảng mô tả target contract, không phải bằng chứng hệ thống đã vận hành.
- Bank integration, chuyển tiền thật, quy trình đối soát với tổ chức tín dụng và tuân thủ giấy phép nằm ngoài phạm vi implementation hiện tại.
- External inspection, email intake, OpenTimestamps, nguồn giá và baseline địa lý có điểm tích hợp/mô phỏng; độ tin cậy production phải được kiểm chứng riêng.
- Các control “zero-trust-oriented” chỉ áp tại một số trust boundary (Gateway, external verifier, identity header, fail-closed). Hệ thống không claim mô hình Zero Trust toàn diện.
- Geolocation/baseline rừng hỗ trợ EUDR evidence, không tự tạo due-diligence statement và không chứng minh tuyệt đối tính đúng của khai báo.
- Mức cọc, cửa sổ xác nhận, release floor, penalty rate trong giới hạn pháp luật, pricing và payer là tham số pilot; chưa phải tối ưu đã được chứng minh.
- Reputation/analytics là projection từ fact cuối cùng. Tín hiệu AML hoặc elevated risk Phase 2 không tự động hold tiền và không thay thế thẩm định con người.
- Không có causal evidence tại Việt Nam để tuyên bố mức giảm vi phạm; cần baseline, cohort và đo trước–sau.

## **8.1 Gói validation sơ cấp trước product–market fit**

Pilot cần xác nhận tối thiểu: (1) anchor buyer và HTX có chấp nhận hai chữ ký cùng terms; (2) mức cọc và lịch milestone có khả thi theo mùa vụ; (3) tỷ lệ tranh chấp Rổ A/Rổ B; (4) thời gian review/giám định; (5) ngân hàng/custodian chấp nhận command–reconciliation contract; (6) willingness-to-pay; và (7) tính dùng được của gói bằng chứng khi có tranh chấp thật.


# **10. Future Work**

- Khảo sát sơ cấp với HTX, buyer, tổ chức giám định và custodian để xác nhận pain frequency, willingness-to-pay, mức cọc và SLA.
- Chạy pilot giới hạn theo commodity/anchor buyer, đo funnel KYC–listing–ký–funding–settlement và baseline vi phạm trước/sau.
- Xác nhận pháp lý cho mẫu contract, tiền cọc, penalty/damages, dữ liệu EUDR và mô hình ngân hàng trước khi có tiền thật.
- Cập nhật competitive discovery và TAM/SAM/SOM bằng số tổ chức, số hợp đồng và ACV đã kiểm chứng thay vì suy từ kim ngạch.

# **11. Danh mục nguồn tham khảo**

[1] G. Ton et al., “The effectiveness of contract farming for raising income of smallholder farmers in low- and middle-income countries: a systematic review,” Campbell Syst. Rev., vol. 13, no. 1, 2017, doi: 10.4073/csr.2017.13.

[4] A. Karakostas, D. M. De Souza Monteiro, and C. Adjei, “Double Moral Hazard in Contract Farming: An Experimental Analysis,” J. Agric. Econ., vol. 76, no. 3, 2025, doi: 10.1111/1477-9552.12646.

[5] D. Alemu, A. Guinan, and J. Hermanson, “Contract farming, cooperatives and challenges of side selling: Malt barley value-chain development in Ethiopia,” Development in Practice, vol. 31, no. 4, 2021, doi: 10.1080/09614524.2020.1860194.

[6] J. Ewusi Koomson, E. Donkor, and V. Owusu, “Contract farming scheme for rubber production in Western region of Ghana: Why do farmers side sell?,” Forests, Trees and Livelihoods, vol. 31, no. 3, 2022, doi: 10.1080/14728028.2022.2079007.

[7] U. S. Umar, S. Rahman, and G. Zanello, “Drivers of farmers’ contract compliance behavior,” Agribusiness, 2025, doi: 10.1002/agr.22039.

[8] J. Upton and E. Lentz, “Finding default? Understanding the drivers of default on contracts with farmers’ organizations,” Agric. Econ., vol. 48, suppl. 1, 2017, doi: 10.1111/agec.12385.

[13] T. T. Pham, L. Theuvsen, and V. Otter, “Determinants of Smallholder Farmers’ Marketing Channel Choice: Evidence from the Vietnamese Rice Sector,” Asian Econ. J., vol. 33, no. 3, 2019, doi: 10.1111/asej.12187.

[14] T. C. Uy et al., “Digital technology adoption among smallholder farmers in Vietnam,” J. Int. Dev., vol. 37, no. 2, 2024, doi: 10.1002/jid.3904.

[17] A. Ruml and M. C. Parlasca, “In-kind credit provision through contract farming and formal credit markets,” Agribusiness, vol. 38, no. 2, 2021, doi: 10.1002/agr.21726.

[18] G. Burtch, Y. Hong, and S. Kumar, “When Does Dispute Resolution Substitute for a Reputation System?,” Prod. Oper. Manag., vol. 30, no. 6, 2020, doi: 10.1111/poms.13341.

[20] J. R. Wolf and W. A. Muhanna, “Feedback Mechanisms, Judgment Bias, and Trust Formation in Online Auctions,” Decision Sciences, vol. 42, no. 1, 2011, doi: 10.1111/j.1540-5915.2010.00301.x.

[23] Bộ Nông nghiệp và Môi trường, “Tổng kết ngành Nông nghiệp và Môi trường năm 2025,” 2026.

[24] Cổng Thông tin điện tử Chính phủ, “Xuất khẩu nông, lâm, thủy sản phấn đấu đạt 73–74 tỷ USD năm 2026,” 2026.

[25] VIAC, “Thống kê hoạt động giải quyết tranh chấp năm 2024,” 2025.

[26] Chính phủ Việt Nam, Nghị định 98/2018/NĐ-CP, 2018.

[27] Quốc hội Việt Nam, Luật Giao dịch điện tử số 20/2023/QH15, 2023.

[28] Chính phủ Việt Nam, Nghị định 52/2024/NĐ-CP, 2024.

[29] Chính phủ Việt Nam, Nghị định 340/2025/NĐ-CP, 2025.

[30] European Parliament and Council, Regulation (EU) 2023/1115, consolidated Dec. 26, 2025.

[31] European Commission, Commission Implementing Regulation (EU) 2025/1093, 2025.

[33] VCCI, “Cần khai thông quyết liệt 4 nút thắt lớn để kinh tế tư nhân bứt phá,” 2026.

[34] Cổng TTĐT Chính phủ, “Gỡ điểm nghẽn tín dụng cho nông nghiệp số, nông nghiệp xanh,” 2026.

[35] VTV, “Giá thu mua tăng cao, cơ hội và thách thức của ngành cà phê Việt Nam,” Apr. 17, 2024.

[36] Saigon Giai Phong, “Vietnamese coffee prices triple, linkage contracts break,” Apr. 11, 2024.

[37] VietnamNet, “Doanh nghiệp đền bù 225 triệu đồng/ha đu đủ vì không thể bao tiêu,” Aug. 18, 2023.

[38] Báo Công Thương, “Vụ việc hủy mua đu đủ: Công ty Nafoods cam kết đền bù 225 triệu đồng/ha,” Aug. 18, 2023.

[39] USDA Foreign Agricultural Service, “Coffee Semi-annual: Vietnam 2024,” GAIN Report VM2024-0047, Dec. 6, 2024.

[40] Cổng TTĐT Chính phủ, “Kinh tế số nông nghiệp gắn với nông thôn số, nông dân số,” 2024.

[41] Thủ tướng Chính phủ, Quyết định 749/QĐ-TTg, 2020.

[42] K. E. Cormier, “Grievance practices in Post-Soviet Kyrgyz agriculture,” Law & Soc. Inq., vol. 32, no. 2, 2007, doi: 10.1111/j.1747-4469.2007.00065.x.

[45] Quốc hội Việt Nam, Bộ luật Dân sự số 91/2015/QH13, 2015.

[46] Quốc hội Việt Nam, Luật Thương mại số 36/2005/QH11, 2005.

[47] Quốc hội Việt Nam, Luật Trọng tài thương mại số 54/2010/QH12, 2010.
