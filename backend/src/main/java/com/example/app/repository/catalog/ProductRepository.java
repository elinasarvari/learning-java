package com.example.app.repository.catalog;

import com.example.app.repository.catalog.entity.ProductEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<ProductEntity, String> {

    List<ProductEntity> findByCategoryOrderByNameAsc(String category);
}
