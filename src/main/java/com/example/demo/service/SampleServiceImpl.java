package com.example.demo.service;

import com.example.demo.entity.SampleItem;
import com.example.demo.repository.SampleItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class SampleServiceImpl implements SampleService {

	private final SampleItemRepository sampleItemRepository;

	public SampleServiceImpl(SampleItemRepository sampleItemRepository) {
		this.sampleItemRepository = sampleItemRepository;
	}

	@Override
	public List<SampleItem> findAll() {
		return sampleItemRepository.findAll();
	}

	@Override
	public SampleItem save(SampleItem item) {
		return sampleItemRepository.save(item);
	}

	@Override
	public Optional<SampleItem> update(Long id, SampleItem item) {
		return sampleItemRepository.findById(id)
				.map(existingItem -> {
					existingItem.setName(item.getName());
					return sampleItemRepository.save(existingItem);
				});
	}

	@Override
	public void delete(Long id) {
		sampleItemRepository.deleteById(id);
	}
}
