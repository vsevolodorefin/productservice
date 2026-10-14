package com.kramp.productservice.domain;

import java.net.URI;
import java.util.List;
import java.util.Map;

public record Product(String id, String name, String description,
                      Map<String, String> specifications, List<URI> images) {
    public Product {
        specifications = Map.copyOf(specifications);
        images = List.copyOf(images);
    }
}
