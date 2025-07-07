package ch.admin.bj.swiyu.verifier.domain;

import ch.admin.bj.swiyu.verifier.common.exception.VerificationException;
import ch.admin.bj.swiyu.verifier.domain.management.Management;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.Constraint;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.Field;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.Filter;
import ch.admin.bj.swiyu.verifier.domain.management.PresentationDefinition.FormatAlgorithm;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CredentialVerifierSupportTest {

    @Test
    void testCheckCommonPresentationDefinitionCriteria_valid() {
        // Mock a credential JSON with vct field
        String credential = "{\"vct\":\"test-value\"}";

        // Mock field with filter on $.vct and constDescriptor "test-value"
        Filter filter = mock(Filter.class);
        when(filter.constDescriptor()).thenReturn("test-value");
        when(filter.type()).thenReturn("string");

        Field field = mock(Field.class);
        when(field.filter()).thenReturn(filter);
        when(field.path()).thenReturn(new java.util.LinkedList<>(List.of("$.vct")));

        Constraint constraint = mock(Constraint.class);
        when(constraint.fields()).thenReturn(List.of(field));

        PresentationDefinition.InputDescriptor descriptor = mock(PresentationDefinition.InputDescriptor.class);
        when(descriptor.constraints()).thenReturn(constraint);

        PresentationDefinition pd = mock(PresentationDefinition.class);
        when(pd.inputDescriptors()).thenReturn(List.of(descriptor));

        Management management = mock(Management.class);
        when(management.getRequestedPresentation()).thenReturn(pd);

        assertDoesNotThrow(() ->
                CredentialVerifierSupport.checkCommonPresentationDefinitionCriteria(credential, management)
        );
    }

    @Test
    void testCheckCommonPresentationDefinitionCriteria_invalidConst() {
        String credential = "{\"vct\":\"wrong-value\"}";

        Filter filter = mock(Filter.class);
        when(filter.constDescriptor()).thenReturn("expected-value");
        when(filter.type()).thenReturn("string");

        Field field = mock(Field.class);
        when(field.filter()).thenReturn(filter);
        when(field.path()).thenReturn(new java.util.LinkedList<>(List.of("$.vct")));

        Constraint constraint = mock(Constraint.class);
        when(constraint.fields()).thenReturn(List.of(field));

        PresentationDefinition.InputDescriptor descriptor = mock(PresentationDefinition.InputDescriptor.class);
        when(descriptor.constraints()).thenReturn(constraint);

        PresentationDefinition pd = mock(PresentationDefinition.class);
        when(pd.inputDescriptors()).thenReturn(List.of(descriptor));

        Management management = mock(Management.class);
        when(management.getRequestedPresentation()).thenReturn(pd);

        VerificationException ex = assertThrows(VerificationException.class, () ->
                CredentialVerifierSupport.checkCommonPresentationDefinitionCriteria(credential, management)
        );
        assertTrue(ex.getErrorDescription().contains("Validation criteria not matched"));
    }

    @Test
    void testGetRequestedFormat_returnsFormatAlgorithm() {
        FormatAlgorithm formatAlgorithm = mock(FormatAlgorithm.class);

        PresentationDefinition pd = mock(PresentationDefinition.class);
        when(pd.format()).thenReturn(Map.of("jwt", formatAlgorithm));
        when(pd.inputDescriptors()).thenReturn(List.of());

        Management management = mock(Management.class);
        when(management.getRequestedPresentation()).thenReturn(pd);

        assertEquals(formatAlgorithm,
                CredentialVerifierSupport.getRequestedFormat("jwt", management));
    }

    @Test
    void testGetRequestedFormat_nullIfNotFound() {
        PresentationDefinition pd = mock(PresentationDefinition.class);
        when(pd.format()).thenReturn(Map.of());
        when(pd.inputDescriptors()).thenReturn(List.of());

        Management management = mock(Management.class);
        when(management.getRequestedPresentation()).thenReturn(pd);

        assertNull(CredentialVerifierSupport.getRequestedFormat("ldp", management));
    }
}