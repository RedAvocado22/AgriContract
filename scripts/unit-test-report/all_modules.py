# All unit-test sheets in report order, each tagged with its test class.
import data_contract, data_gateway_bank, data_escrow_notification, data_inspection
import data_product_domain, data_product_app, data_reputation, data_shared, data_user

TEST_CLASS = {
    "Contract": "ContractTest",
    "CreateContract": "CreateContractUseCaseTest",
    "GetContract": "GetContractUseCaseTest",
    "ListContracts": "ListContractsUseCaseTest",
    "NegotiateContract": "NegotiateContractUseCaseTest",
    "SignContract": "SignContractUseCaseTest",
    "UserContextInjectionFilter": "UserContextInjectionFilterTest",
    "LedgerEntry": "LedgerEntryTest",
    "Money": "MoneyTest",
    "LockRequestConsumer": "LockRequestConsumerTest",
    "ProcessLockRequest": "ProcessLockRequestUseCaseImplTest",
    "ContractSignedConsumer": "ContractSignedConsumerTest",
    "RequestActivationDeposits": "RequestActivationDepositsUseCaseImplTest",
    "DepositLeg": "DepositLegTest",
    "ProcessNotification": "ProcessNotificationUseCaseImplTest",
    "NotificationEventConsumer": "NotificationEventConsumerTest",
    "InspectionAuthorization": "InspectionAuthorizationTest",
    "InspectorEligibilityGate": "InspectorEligibilityGateTest",
    "InternalUserEligibility": "InternalUserEligibilityClientTest",
    "GatewayAuthenticationFilter": "GatewayAuthenticationFilterTest",
    "Reservation": "ReservationTest",
    "PlotRegistryEntry": "PlotRegistryEntryTest",
    "Category": "CategoryTest",
    "Listing": "ListingTest",
    "Product": "ProductTest",
    "PlotGeometries": "PlotGeometriesTest",
    "ApproveCategory": "ApproveCategoryUseCaseImplTest",
    "ProposeCategory": "ProposeCategoryUseCaseImplTest",
    "RejectCategory": "RejectCategoryUseCaseImplTest",
    "ImportPlotsFromKml": "ImportPlotsFromKmlUseCaseImplTest",
    "PlotRegistrar": "PlotRegistrarTest",
    "CreateListing": "CreateListingUseCaseImplTest",
    "CreateProduct": "CreateProductUseCaseImplTest",
    "UpdateProductImages": "UpdateProductImagesUseCaseImplTest",
    "KmlPlacemarkReader": "KmlPlacemarkReaderTest",
    "ApplicationBoundary": "ApplicationBoundaryTest",
    "FactAndScore": "FactAndScoreTest",
    "RabbitTopology": "RabbitTopologyTest",
    "FrozenEventContract": "FrozenEventContractTest",
    "DlqMonitor": "DlqMonitorTest",
    "ReputationFactConsumer": "ReputationFactConsumerTest",
    "GatewayConfiguration": "GatewayConfigurationTest",
    "ErrorEnvelopeHandler": "ErrorEnvelopeExceptionHandlerTest",
    "EventContext": "EventContextTest",
    "EventEnvelope": "EventEnvelopeTest",
    "EventSubject": "EventSubjectTest",
    "EventEnvelopeCodec": "EventEnvelopeCodecTest",
    "UserProfile": "UserProfileTest",
    "AssignUserRole": "AssignUserRoleUseCaseImplTest",
    "GetInternalUserInfo": "GetInternalUserInfoUseCaseImplTest",
    "GetKycProfile": "GetKycProfileUseCaseImplTest",
    "ListUsersByVerification": "ListUsersByVerificationStatusUseCaseImplTest",
    "RejectUser": "RejectUserUseCaseImplTest",
    "VerifyUser": "VerifyUserUseCaseImplTest",
}

MODULES = []
for mod in (data_gateway_bank, data_contract, data_escrow_notification, data_inspection,
            data_product_domain, data_product_app, data_reputation, data_shared, data_user):
    MODULES.extend(mod.MODULES)
# keep services together, in alphabetical order (gateway first: it sorts first anyway)
MODULES.sort(key=lambda m: m["module"])

names = [m["sheet"] for m in MODULES]
assert len(names) == len(set(names)), "duplicate sheet name"
assert all(len(n) <= 31 for n in names), [n for n in names if len(n) > 31]
assert set(names) == set(TEST_CLASS), set(names) ^ set(TEST_CLASS)
for m in MODULES:
    m["test_class"] = TEST_CLASS[m["sheet"]]
