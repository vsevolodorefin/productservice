package com.kramp.productservice.application;

public class AggregationNotImplementedException extends RuntimeException {
    public AggregationNotImplementedException() {
        super("Product aggregation is not implemented yet.");
    }
}
