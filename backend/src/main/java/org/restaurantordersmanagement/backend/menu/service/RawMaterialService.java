package org.restaurantordersmanagement.backend.menu.service;

import java.util.List;
import org.restaurantordersmanagement.backend.menu.model.RawMaterial;
import org.restaurantordersmanagement.backend.menu.repository.RawMaterialRepository;
import org.restaurantordersmanagement.backend.menu.web.RawMaterialRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Unlike MealService/CategoryService, no method here needs @Transactional:
 * RawMaterial has only scalar fields (no translations, no nested
 * collections), so a detached instance returned by one repository call
 * remains fully usable in the next, with no session required.
 */
@Service
public class RawMaterialService {

    private final RawMaterialRepository rawMaterialRepository;
    private final ImageStorageService imageStorageService;

    public RawMaterialService(RawMaterialRepository rawMaterialRepository, ImageStorageService imageStorageService) {
        this.rawMaterialRepository = rawMaterialRepository;
        this.imageStorageService = imageStorageService;
    }

    public List<RawMaterial> findAll() {
        return rawMaterialRepository.findAll();
    }

    public RawMaterial findById(Long id) {
        return rawMaterialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Raw material not found"));
    }

    public RawMaterial create(RawMaterialRequest request) {
        RawMaterial rawMaterial = new RawMaterial();
        applyRequest(rawMaterial, request);
        return rawMaterialRepository.saveAndFlush(rawMaterial);
    }

    public RawMaterial update(Long id, RawMaterialRequest request) {
        RawMaterial rawMaterial = findById(id);
        applyRequest(rawMaterial, request);
        return rawMaterialRepository.saveAndFlush(rawMaterial);
    }

    /**
     * Deletes the DB row (and flushes, so a Recipe still referencing it
     * throws here) before deleting the image file - a blocked delete must
     * never orphan the image.
     */
    public void delete(Long id) {
        RawMaterial rawMaterial = findById(id);
        rawMaterialRepository.deleteById(id);
        rawMaterialRepository.flush();
        imageStorageService.delete(rawMaterial.getImagePath());
    }

    public RawMaterial uploadImage(Long id, MultipartFile file) {
        RawMaterial rawMaterial = findById(id);
        String previousPath = rawMaterial.getImagePath();
        rawMaterial.setImagePath(imageStorageService.store(file, "raw-materials"));
        RawMaterial saved = rawMaterialRepository.saveAndFlush(rawMaterial);
        imageStorageService.delete(previousPath);
        return saved;
    }

    public RawMaterial deleteImage(Long id) {
        RawMaterial rawMaterial = findById(id);
        imageStorageService.delete(rawMaterial.getImagePath());
        rawMaterial.setImagePath(null);
        return rawMaterialRepository.saveAndFlush(rawMaterial);
    }

    private void applyRequest(RawMaterial rawMaterial, RawMaterialRequest request) {
        rawMaterial.setName(request.name());
        rawMaterial.setUnit(request.unit());
        rawMaterial.setInStockQuantity(request.inStockQuantity());
        rawMaterial.setSupplier(request.supplier());
    }

}
