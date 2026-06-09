package com.example.demo.controller;

import com.example.demo.entity.SampleItem;
import com.example.demo.service.SampleService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@CrossOrigin(origins = "*")
@RequestMapping("/api/items")
public class SampleController {

	private final SampleService sampleService;

	public SampleController(SampleService sampleService) {
		this.sampleService = sampleService;
	}

	@GetMapping
	public List<SampleItem> findAll() {
		return sampleService.findAll();
	}

	@PostMapping
	public SampleItem create(@RequestBody SampleItem item) {
		return sampleService.save(item);
	}

	@PutMapping("/{id}")
	public ResponseEntity<SampleItem> update(@PathVariable Long id, @RequestBody SampleItem item) {
		return sampleService.update(id, item)
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void delete(@PathVariable Long id) {
		sampleService.delete(id);
	}
}
