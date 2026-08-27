package cn.iocoder.yudao.module.commerce.service.product;

import cn.hutool.core.util.StrUtil;
import cn.iocoder.yudao.framework.common.enums.CommonStatusEnum;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryCreateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryTreeRespVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateReqVO;
import cn.iocoder.yudao.module.commerce.controller.admin.product.vo.category.ProductCategoryUpdateStatusReqVO;
import cn.iocoder.yudao.module.commerce.dal.dataobject.product.ProductCategoryDO;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductCategoryMapper;
import cn.iocoder.yudao.module.commerce.dal.mysql.product.ProductMapper;
import cn.iocoder.yudao.module.infra.api.file.FileApi;
import jakarta.annotation.Resource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static cn.iocoder.yudao.framework.common.exception.util.ServiceExceptionUtil.exception;
import static cn.iocoder.yudao.module.commerce.enums.ErrorCodeConstants.*;

@Service
public class ProductCategoryServiceImpl implements ProductCategoryService {

    @Resource private ProductCategoryMapper categoryMapper;
    @Resource private ProductMapper productMapper;
    @Resource private FileApi fileApi;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createCategory(ProductCategoryCreateReqVO reqVO) {
        List<ProductCategoryDO> categories = categoryMapper.selectListOrderedForUpdate();
        Map<Long, ProductCategoryDO> byId = validateSnapshot(categories);
        String code = normalizeCode(reqVO.getCode());
        if (reqVO.getParentId() != null && !byId.containsKey(reqVO.getParentId())) {
            throw exception(PRODUCT_CATEGORY_PARENT_NOT_EXISTS);
        }
        try {
            ProductCategoryDO category = new ProductCategoryDO()
                    .setParentId(reqVO.getParentId()).setCode(code)
                    .setName(StrUtil.trim(reqVO.getName()))
                    .setImageUrl(StrUtil.trim(reqVO.getImageUrl()))
                    .setSort(reqVO.getSort() == null ? 0 : reqVO.getSort())
                    .setStatus(CommonStatusEnum.DISABLE.getStatus());
            categoryMapper.insert(category);
            bindCategoryImage(category.getId(), category.getImageUrl());
            return category.getId();
        } catch (DuplicateKeyException ex) {
            throw exception(PRODUCT_CATEGORY_CODE_DUPLICATE);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategory(ProductCategoryUpdateReqVO reqVO) {
        List<ProductCategoryDO> categories = categoryMapper.selectListOrderedForUpdate();
        Map<Long, ProductCategoryDO> byId = validateSnapshot(categories);
        ProductCategoryDO current = requireCategory(byId, reqVO.getId());
        validateParentChange(reqVO.getId(), reqVO.getParentId(), byId);
        if (CommonStatusEnum.ENABLE.getStatus().equals(current.getStatus())) {
            requireEnabledAncestors(reqVO.getParentId(), byId);
        }
        int updated = categoryMapper.updateDetails(new ProductCategoryDO().setId(reqVO.getId())
                .setParentId(reqVO.getParentId()).setName(StrUtil.trim(reqVO.getName()))
                .setImageUrl(StrUtil.trim(reqVO.getImageUrl()))
                .setSort(reqVO.getSort() == null ? 0 : reqVO.getSort()));
        if (updated != 1) {
            throw exception(PRODUCT_CATEGORY_STATE_CONFLICT);
        }
        bindCategoryImage(reqVO.getId(), reqVO.getImageUrl());
    }

    private void bindCategoryImage(Long categoryId, String imageUrl) {
        fileApi.replaceFileReferences("commerce_product_category", categoryId.toString(), "image",
                StrUtil.isBlank(imageUrl) ? List.of() : List.of(imageUrl.trim()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateCategoryStatus(ProductCategoryUpdateStatusReqVO reqVO) {
        List<ProductCategoryDO> categories = categoryMapper.selectListOrderedForUpdate();
        Map<Long, ProductCategoryDO> byId = validateSnapshot(categories);
        ProductCategoryDO current = requireCategory(byId, reqVO.getId());
        if (current.getStatus().equals(reqVO.getStatus())) {
            throw exception(PRODUCT_CATEGORY_STATE_CONFLICT);
        }
        if (CommonStatusEnum.ENABLE.getStatus().equals(reqVO.getStatus())) {
            requireEnabledAncestors(current.getParentId(), byId);
        } else {
            List<Long> descendantIds = descendantIds(current.getId(), byId);
            if (descendantIds.stream().map(byId::get).anyMatch(item ->
                    CommonStatusEnum.ENABLE.getStatus().equals(item.getStatus()))) {
                throw exception(PRODUCT_CATEGORY_ENABLED_DESCENDANT);
            }
            List<Long> subtreeIds = new ArrayList<>(descendantIds);
            subtreeIds.add(current.getId());
            if (productMapper.countOnSaleByCategoryIds(subtreeIds) > 0) {
                throw exception(PRODUCT_CATEGORY_SUBTREE_PRODUCT_ON_SALE);
            }
        }
        if (categoryMapper.updateStatusExpected(reqVO.getId(), current.getStatus(), reqVO.getStatus()) != 1) {
            throw exception(PRODUCT_CATEGORY_STATE_CONFLICT);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductCategoryTreeRespVO> getCategoryTree() {
        Map<Long, ProductCategoryDO> byId = validateSnapshot(categoryMapper.selectListOrdered());
        Map<Long, ProductCategoryTreeRespVO> responseById = new HashMap<>();
        List<ProductCategoryTreeRespVO> roots = new ArrayList<>();
        Comparator<ProductCategoryDO> ordering = Comparator.comparing(ProductCategoryDO::getSort)
                .thenComparing(ProductCategoryDO::getId);
        byId.values().stream().sorted(ordering).forEach(category ->
                responseById.put(category.getId(), toResponse(category)));
        byId.values().stream().sorted(ordering).forEach(category -> {
            ProductCategoryTreeRespVO response = responseById.get(category.getId());
            if (category.getParentId() == null) {
                roots.add(response);
            } else {
                ProductCategoryTreeRespVO parent = responseById.get(category.getParentId());
                if (parent == null) {
                    throw exception(PRODUCT_CATEGORY_TREE_INVALID);
                }
                parent.getChildren().add(response);
            }
        });
        return roots;
    }

    @Override
    public List<ProductCategoryDO> lockCategorySnapshot() {
        List<ProductCategoryDO> categories = categoryMapper.selectListOrderedForUpdate();
        validateSnapshot(categories);
        return categories;
    }

    @Override
    public void requireEnabledCategory(Long categoryId, List<ProductCategoryDO> categories) {
        Map<Long, ProductCategoryDO> byId = validateSnapshot(categories);
        ProductCategoryDO category = byId.get(categoryId);
        if (category == null) {
            throw exception(PRODUCT_CATEGORY_NOT_EXISTS);
        }
        Long currentId = categoryId;
        while (currentId != null) {
            ProductCategoryDO current = byId.get(currentId);
            if (current == null) {
                throw exception(PRODUCT_CATEGORY_TREE_INVALID);
            }
            if (!CommonStatusEnum.ENABLE.getStatus().equals(current.getStatus())) {
                throw exception(PRODUCT_CATEGORY_DISABLED);
            }
            currentId = current.getParentId();
        }
    }

    private ProductCategoryTreeRespVO toResponse(ProductCategoryDO category) {
        return new ProductCategoryTreeRespVO().setId(category.getId()).setParentId(category.getParentId())
                .setCode(category.getCode()).setName(category.getName()).setImageUrl(category.getImageUrl())
                .setSort(category.getSort()).setStatus(category.getStatus());
    }

    private Map<Long, ProductCategoryDO> validateSnapshot(List<ProductCategoryDO> categories) {
        Map<Long, ProductCategoryDO> byId = categories.stream().collect(Collectors.toMap(
                ProductCategoryDO::getId, item -> item, (left, right) -> {
                    throw exception(PRODUCT_CATEGORY_TREE_INVALID);
                }, HashMap::new));
        for (ProductCategoryDO category : categories) {
            Set<Long> visited = new HashSet<>();
            Long currentId = category.getId();
            while (currentId != null) {
                if (!visited.add(currentId)) {
                    throw exception(PRODUCT_CATEGORY_TREE_INVALID);
                }
                ProductCategoryDO current = byId.get(currentId);
                if (current == null) {
                    throw exception(PRODUCT_CATEGORY_TREE_INVALID);
                }
                currentId = current.getParentId();
            }
        }
        return byId;
    }

    private ProductCategoryDO requireCategory(Map<Long, ProductCategoryDO> byId, Long id) {
        ProductCategoryDO category = byId.get(id);
        if (category == null) {
            throw exception(PRODUCT_CATEGORY_NOT_EXISTS);
        }
        return category;
    }

    private void validateParentChange(Long categoryId, Long parentId, Map<Long, ProductCategoryDO> byId) {
        if (parentId == null) return;
        if (categoryId.equals(parentId)) throw exception(PRODUCT_CATEGORY_PARENT_SELF);
        if (!byId.containsKey(parentId)) throw exception(PRODUCT_CATEGORY_PARENT_NOT_EXISTS);
        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while (currentId != null) {
            if (!visited.add(currentId)) throw exception(PRODUCT_CATEGORY_PARENT_CYCLE);
            if (categoryId.equals(currentId)) throw exception(PRODUCT_CATEGORY_PARENT_CYCLE);
            ProductCategoryDO current = byId.get(currentId);
            if (current == null) throw exception(PRODUCT_CATEGORY_TREE_INVALID);
            currentId = current.getParentId();
        }
    }

    private void requireEnabledAncestors(Long parentId, Map<Long, ProductCategoryDO> byId) {
        Long currentId = parentId;
        while (currentId != null) {
            ProductCategoryDO parent = byId.get(currentId);
            if (parent == null) throw exception(PRODUCT_CATEGORY_TREE_INVALID);
            if (!CommonStatusEnum.ENABLE.getStatus().equals(parent.getStatus())) {
                throw exception(PRODUCT_CATEGORY_ANCESTOR_DISABLED);
            }
            currentId = parent.getParentId();
        }
    }

    private List<Long> descendantIds(Long categoryId, Map<Long, ProductCategoryDO> byId) {
        Map<Long, List<Long>> children = new HashMap<>();
        byId.values().forEach(item -> children.computeIfAbsent(item.getParentId(), ignored -> new ArrayList<>())
                .add(item.getId()));
        List<Long> result = new ArrayList<>();
        ArrayDeque<Long> queue = new ArrayDeque<>(children.getOrDefault(categoryId, List.of()));
        while (!queue.isEmpty()) {
            Long id = queue.removeFirst();
            result.add(id);
            queue.addAll(children.getOrDefault(id, List.of()));
        }
        return result;
    }

    private String normalizeCode(String code) {
        String normalized = StrUtil.trim(code);
        if (!normalized.matches("[a-z0-9_]{2,32}")) {
            throw exception(PRODUCT_CATEGORY_CODE_DUPLICATE);
        }
        return normalized;
    }
}
