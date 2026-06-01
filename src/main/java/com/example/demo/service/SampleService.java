package com.example.demo.service;

import com.example.demo.entity.SampleItem;

import java.util.List;
import java.util.Optional;

public interface SampleService {

	List<SampleItem> findAll();

	SampleItem save(SampleItem item);

	Optional<SampleItem> update(Long id, SampleItem item);

	void delete(Long id);
}
