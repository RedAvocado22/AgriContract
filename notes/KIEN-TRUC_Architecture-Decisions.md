# Kiến trúc & Quyết định kỹ thuật — AgriContract

> Ghi lại các quyết định thiết kế đã thống nhất để cả team hiểu lý do, tránh bị hỏi lại.
> Cập nhật lần cuối: 19/06/2026

---

## 1. Clean Architecture vs Hexagonal — dự án dùng cái nào và tại sao khác nhau

Dự án theo **Clean Architecture** (Uncle Bob), không phải Hexagonal thuần.

### Điểm khác nhau cốt lõi

| | Hexagonal (Ports & Adapters) | Clean Architecture |
|---|---|---|
| Tác giả | Alistair Cockburn | Robert C. Martin (Uncle Bob) |
| Bên ngoài | Ports (interfaces) + Adapters (impls) | Interface Adapters + Frameworks & Drivers |
| Bên trong | **Không quy định** — tự tổ chức | **4 vòng tròn cụ thể** (xem bên dưới) |
| Dependency rule | Inward (adapters → ports → core) | Cứng hơn: dependencies chỉ đi vào trong, không bao giờ ra |

### 4 vòng tròn của Clean Architecture

```
┌──────────────────────────────────────┐
│  Frameworks & Drivers                │  ← Spring, JPA, Feign, RabbitMQ
│  ┌────────────────────────────────┐  │
│  │  Interface Adapters            │  │  ← Controllers, RepositoryImpl, FeignClient
│  │  ┌──────────────────────────┐  │  │
│  │  │  Use Cases               │  │  │  ← GetListingUseCase, NotificationConsumer
│  │  │  ┌────────────────────┐  │  │  │
│  │  │  │  Entities (Domain) │  │  │  │  ← Listing, UserProfile, NotificationLog
│  │  │  └────────────────────┘  │  │  │
│  │  └──────────────────────────┘  │  │
│  └────────────────────────────────┘  │
└──────────────────────────────────────┘
```

**Dependency Rule:** code ở vòng ngoài được phép phụ thuộc vào vòng trong. Code ở vòng trong **tuyệt đối không** import class của vòng ngoài.

Ví dụ sai:
```java
// WRONG — Use Case import Spring annotation
@Service
public class GetListingUseCaseImpl {
    @Autowired  // ← Spring leak vào use case layer
    ...
}
```

Ví dụ đúng: `@Service` đặt ở Impl, interface không có Spring annotation.

---

## 2. Gateway Pattern cho Service-to-Service (Feign)

Khi một service gọi service khác qua HTTP/Feign, **Feign client là adapter** — không inject thẳng vào use case.

### Tại sao?

Use case phụ thuộc trực tiếp vào `FeignClient` là vi phạm Dependency Rule — Feign là framework (vòng ngoài), use case là vòng trong. Nếu sau này đổi Feign sang gRPC thì phải sửa use case.

### Pattern đúng

```
application/port/out/UserServicePort.java        ← interface (use case layer)
infrastructure/client/UserServiceFeignClient.java ← implements UserServicePort
```

```java
// Use case chỉ biết đến interface này
public interface UserServicePort {
    UserInfo getUserById(String userId); // return null nếu lỗi
}

// Feign là adapter ở infrastructure
@Component
public class UserServiceFeignClient implements UserServicePort {
    // @FeignClient config ở đây
    // error handling: log WARN + return null — không throw
}
```

### Áp dụng vào project

| Service | Interface (use case layer) | Adapter (infrastructure) |
|---|---|---|
| notification-service → user-service | `UserServicePort` | `UserServiceFeignClient` |
| contract-service → product-service | *(NMC tự quyết)* | *(NMC tự quyết)* |

**Lưu ý notification-service:** Feign lỗi → log WARN + return null, **không throw**. Lý do: Feign failure không được cản trở việc tạo `NotificationLog`. Error handling đặt ở adapter, không ở use case.

### Công ty lớn làm gì

- **Netflix** — tác giả của Feign (Netflix OSS). Toàn bộ microservices Netflix dùng gateway interface, Feign là adapter ngoài cùng.
- **Amazon** — ưu tiên async (SQS/SNS) cho service-to-service. Sync (Feign/gRPC) chỉ khi **bắt buộc cần response ngay**.
- **Microsoft eShopOnContainers** — gateway interface + HttpClient impl, use case không bao giờ biết HttpClient tồn tại.

---

## 3. Pagination — tại sao `Pageable` đặt trong domain interface

### Vấn đề

`Pageable` là class của `spring-data-commons`. Đặt nó trong `domain/repository` là đưa framework dependency vào domain layer — về lý thuyết vi phạm Clean Architecture.

### 3 hướng giải quyết (đã research)

| Hướng | Mô tả | Dùng khi |
|---|---|---|
| CQRS split | Read query tách khỏi domain repo, dùng raw SQL/JPQL thẳng | Team lớn, long-term project |
| Domain `PageQuery` VO | Tạo `PageQuery(page, size, sort)` VO trong domain, infra translate sang `Pageable` | Muốn domain hoàn toàn framework-agnostic |
| **Pragmatic (dự án này)** | `Pageable` thẳng vào domain interface | **Team nhỏ, deadline gấp** |

### Tại sao chọn Pragmatic

`spring-data-commons` không phải JPA-specific — nó là abstraction layer chung, không gắn với Hibernate hay bất kỳ DB nào. Overhead của việc abstract thêm `PageQuery` không đáng so với deadline 24/07.

### Quy tắc paginate

| Method | Paginate? | Lý do |
|---|---|---|
| `findByStatus(status, Pageable)` | ✅ | User-facing list — cần paging |
| `findBySellerId(sellerId, Pageable)` | ✅ | User-facing list — seller có thể có nhiều listing |
| `findByStatusAndDeliveryDeadlineBefore(status, date)` | ❌ | Batch job — cần xử lý ALL records, paginate thì bug |

---

## 4. Async vs Sync — khi nào dùng cái nào

| Pattern | Dùng khi | Ví dụ trong dự án |
|---|---|---|
| **Async (RabbitMQ event)** | Caller không cần response ngay, operation có thể retry | contract-service publish ContractSignedEvent → notification-service gửi email |
| **Sync (Feign HTTP)** | Caller cần data ngay để tiếp tục xử lý | notification-service lấy email user trước khi gửi |

Nguyên tắc của Amazon: nếu caller **không cần response ngay** → async. Sync chỉ là lựa chọn cuối.
