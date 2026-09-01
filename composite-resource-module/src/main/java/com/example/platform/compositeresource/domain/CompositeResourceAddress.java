package com.example.platform.compositeresource.domain;

public sealed interface CompositeResourceAddress
        permits WholeResourceAddress, FacetAddress, ComponentAddress {}
