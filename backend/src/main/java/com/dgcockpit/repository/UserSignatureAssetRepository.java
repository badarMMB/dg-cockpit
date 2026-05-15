package com.dgcockpit.repository;

import com.dgcockpit.entity.UserSignatureAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserSignatureAssetRepository extends JpaRepository<UserSignatureAsset, String> {
    List<UserSignatureAsset> findByUserIdAndActiveTrue(String userId);
}
