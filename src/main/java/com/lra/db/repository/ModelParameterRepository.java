package com.lra.db.repository;

import com.lra.db.entity.ModelParameter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ModelParameterRepository extends JpaRepository<ModelParameter, String> {

    List<ModelParameter> findByProcessIdAndIsActive(String processId, boolean isActive);

    List<ModelParameter> findByProcessIdAndParamKeyAndIsActive(
            String processId, String paramKey, boolean isActive);

    Optional<ModelParameter> findFirstByProcessIdAndParamKeyOrderByVersionDesc(
            String processId, String paramKey);
}
