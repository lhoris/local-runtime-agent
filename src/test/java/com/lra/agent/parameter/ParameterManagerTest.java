package com.lra.agent.parameter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lra.db.entity.ModelParameter;
import com.lra.db.repository.ModelParameterRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ParameterManagerTest {

    private static final String PROCESS_ID = "proc-1";

    @Mock
    private ModelParameterRepository parameterRepository;

    private DefaultParameterManager parameterManager;

    @BeforeEach
    void setUp() {
        parameterManager = new DefaultParameterManager(parameterRepository, new ObjectMapper());
    }

    private ModelParameter param(String key, String value, ParameterType type,
                                 int version, boolean active) {
        ModelParameter p = new ModelParameter();
        p.setParamId(key + "-v" + version);
        p.setProcessId(PROCESS_ID);
        p.setParamKey(key);
        p.setParamValue(value);
        p.setParamType(type.name());
        p.setVersion(version);
        p.setIsActive(active);
        return p;
    }

    @Test
    void testLoadParameters() {
        when(parameterRepository.findByProcessIdAndIsActive(PROCESS_ID, true))
                .thenReturn(List.of(param("temperature", "0.7", ParameterType.FLOAT, 1, true)));

        Map<String, Object> params = parameterManager.loadParameters(PROCESS_ID);

        assertEquals(0.7f, params.get("temperature"));
    }

    @Test
    void testApplyParameters() {
        when(parameterRepository.findByProcessIdAndIsActive(PROCESS_ID, true))
                .thenReturn(List.of(
                        param("ENV_MODEL_TYPE", "llama", ParameterType.STRING, 1, true),
                        param("ARG_temperature", "0.7", ParameterType.FLOAT, 1, true)));

        Map<String, String> envVars = new HashMap<>();
        List<String> args = new ArrayList<>();

        parameterManager.applyParameters(PROCESS_ID, envVars, args);

        assertTrue(envVars.containsKey("MODEL_TYPE"));
        assertEquals("llama", envVars.get("MODEL_TYPE"));
        assertTrue(args.contains("--temperature"));
        assertEquals("0.7", args.get(args.indexOf("--temperature") + 1));
    }

    @Test
    void testValidateParameter_Valid() {
        assertDoesNotThrow(() ->
                parameterManager.validateParameter("polling_interval_sec", "30"));
    }

    @Test
    void testValidateParameter_Invalid() {
        assertThrows(ValidationException.class, () ->
                parameterManager.validateParameter("polling_interval_sec", "5"));
    }

    @Test
    void testUpdateParameter() throws ValidationException {
        parameterManager.updateParameter(PROCESS_ID, "temperature", "0.8");

        verify(parameterRepository).save(argThat(p ->
                "temperature".equals(p.getParamKey())
                        && "0.8".equals(p.getParamValue())
                        && Boolean.TRUE.equals(p.getIsActive())
                        && p.getVersion() == 1));
    }

    @Test
    void testUpdateParameter_DeactivatesPriorVersion() throws ValidationException {
        ModelParameter prior = param("temperature", "0.7", ParameterType.FLOAT, 1, true);
        when(parameterRepository.findByProcessIdAndParamKeyAndIsActive(PROCESS_ID, "temperature", true))
                .thenReturn(List.of(prior));
        when(parameterRepository.findFirstByProcessIdAndParamKeyOrderByVersionDesc(PROCESS_ID, "temperature"))
                .thenReturn(java.util.Optional.of(prior));

        parameterManager.updateParameter(PROCESS_ID, "temperature", "0.8");

        verify(parameterRepository).save(argThat(p ->
                "temperature".equals(p.getParamKey()) && Boolean.FALSE.equals(p.getIsActive())));
        verify(parameterRepository).save(argThat(p ->
                "0.8".equals(p.getParamValue()) && p.getVersion() == 2));
    }
}
