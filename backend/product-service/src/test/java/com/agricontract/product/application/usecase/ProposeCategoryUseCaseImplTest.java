package com.agricontract.product.application.usecase;

import com.agricontract.product.application.dto.CategoryResponse;
import com.agricontract.product.application.dto.ProposeCategoryRequest;
import com.agricontract.product.application.port.UserContactPort;
import com.agricontract.product.common.exception.DuplicateCategoryException;
import com.agricontract.product.common.exception.UserServiceUnavailableException;
import com.agricontract.product.domain.model.Category;
import com.agricontract.product.domain.model.vo.CategoryId;
import com.agricontract.product.domain.repository.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProposeCategoryUseCaseImplTest {

    @Mock private CategoryRepository categoryRepository;
    @Mock private UserContactPort userContactPort;

    private ProposeCategoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ProposeCategoryUseCaseImpl(categoryRepository, userContactPort);
    }

    @Test
    void execute_newName_savesWithEmailFromUserServiceAndReturnsPending() {
        when(categoryRepository.findByNormalizedName("ca phe")).thenReturn(Optional.empty());
        when(userContactPort.getEmail("seller-1")).thenReturn("seller@test.com");
        when(categoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CategoryResponse response = useCase.execute(new ProposeCategoryRequest("category-1", "Cà Phê"), "seller-1");

        assertThat(response.categoryId()).isEqualTo("category-1");
        assertThat(response.name()).isEqualTo("Cà Phê");
        ArgumentCaptor<Category> saved = ArgumentCaptor.forClass(Category.class);
        verify(categoryRepository).save(saved.capture());
        assertThat(saved.getValue().getProposedByEmail()).isEqualTo("seller@test.com");
    }

    @Test
    void execute_duplicateNormalizedName_throwsWithoutCallingUserService() {
        Category existing = Category.propose(new CategoryId("category-0"), "ca phe", "ca phe", "other", "other@test.com");
        when(categoryRepository.findByNormalizedName("ca phe")).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> useCase.execute(new ProposeCategoryRequest(null, "Ca Phe"), "seller-1"))
                .isInstanceOf(DuplicateCategoryException.class);

        verifyNoInteractions(userContactPort);
        verify(categoryRepository, never()).save(any());
    }

    @Test
    void execute_userServiceUnavailable_failsClosedWithoutSaving() {
        when(categoryRepository.findByNormalizedName("ca phe")).thenReturn(Optional.empty());
        when(userContactPort.getEmail("seller-1"))
                .thenThrow(new UserServiceUnavailableException("down", null));

        assertThatThrownBy(() -> useCase.execute(new ProposeCategoryRequest(null, "Ca Phe"), "seller-1"))
                .isInstanceOf(UserServiceUnavailableException.class);

        verify(categoryRepository, never()).save(any());
    }

    @Test
    void execute_concurrentDuplicateRace_dbConstraintMappedToDuplicateException() {
        when(categoryRepository.findByNormalizedName("ca phe")).thenReturn(Optional.empty());
        when(userContactPort.getEmail("seller-1")).thenReturn("seller@test.com");
        when(categoryRepository.save(any())).thenThrow(new DataIntegrityViolationException("uq_categories_normalized_name"));

        assertThatThrownBy(() -> useCase.execute(new ProposeCategoryRequest(null, "Ca Phe"), "seller-1"))
                .isInstanceOf(DuplicateCategoryException.class);
    }
}
