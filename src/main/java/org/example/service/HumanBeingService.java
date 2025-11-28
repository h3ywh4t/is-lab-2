package org.example.service;

import org.example.dto.HumanBeingDTO;
import org.example.entity.*;
import org.example.mapper.HumanBeingMapper;
import org.example.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class HumanBeingService {

    @Autowired
    private HumanBeingRepository humanBeingRepository;

    @Autowired
    private CarRepository carRepository;

    @Autowired
    private CoordinatesRepository coordinatesRepository;

    @Autowired
    private HumanBeingMapper mapper;

    public Page<HumanBeingDTO> getAllHumanBeings(Pageable pageable, String filterBy, String filterValue) {
        Page<HumanBeing> result;

        if (filterBy != null && filterValue != null && !filterValue.trim().isEmpty()) {
            switch (filterBy) {
                case "name":
                    result = humanBeingRepository.findByNameExact(filterValue.trim(), pageable);
                    break;
                case "car.name":
                    result = humanBeingRepository.findByCarNameExact(filterValue.trim(), pageable);
                    break;
                case "mood":
                    result = humanBeingRepository.findByMoodExact(filterValue.trim(), pageable);
                    break;
                case "weaponType":
                    result = humanBeingRepository.findByWeaponTypeExact(filterValue.trim(), pageable);
                    break;
                default:
                    result = humanBeingRepository.findAll(pageable);
            }
        } else {
            result = humanBeingRepository.findAll(pageable);
        }

        return result.map(mapper::toDTO);
    }

    public HumanBeingDTO getHumanBeingById(Long id) {
        return humanBeingRepository.findById(id)
                .map(mapper::toDTO)
                .orElseThrow(() -> new RuntimeException("HumanBeing not found with id: " + id));
    }

    public HumanBeingDTO createHumanBeing(HumanBeingDTO dto) {
        HumanBeing entity = mapper.toEntity(dto);

        if (dto.getCoordinates() != null) {
            Coordinates coords;
            if (dto.getCoordinates().getId() == null) {
                coords = mapper.toCoordinatesEntity(dto.getCoordinates());
                if (coords.getY() != null && coords.getY() <= -926) {
                    throw new IllegalArgumentException("Y coordinate must be greater than -926");
                }
                if (coords.getX() != null && coords.getX() > 112) {
                    throw new IllegalArgumentException("X coordinate cannot exceed 112");
                }
                coords = coordinatesRepository.save(coords);
            } else {
                coords = coordinatesRepository.findById(dto.getCoordinates().getId())
                        .orElseThrow(() -> new RuntimeException("Coordinates not found"));
                if (dto.getCoordinates().getX() != null) coords.setX(dto.getCoordinates().getX());
                if (dto.getCoordinates().getY() != null) coords.setY(dto.getCoordinates().getY());
                if (coords.getY() != null && coords.getY() <= -926) {
                    throw new IllegalArgumentException("Y coordinate must be greater than -926");
                }
                if (coords.getX() != null && coords.getX() > 112) {
                    throw new IllegalArgumentException("X coordinate cannot exceed 112");
                }
                coords = coordinatesRepository.save(coords);
            }
            entity.setCoordinates(coords);
        }

        if (dto.getCar() != null && dto.getCar().getId() == null) {
            Car car = mapper.toCarEntity(dto.getCar());
            car = carRepository.save(car);
            entity.setCar(car);
        } else if (dto.getCar() != null && dto.getCar().getId() != null) {
            Car car = carRepository.findById(dto.getCar().getId())
                    .orElseThrow(() -> new RuntimeException("Car not found"));
            entity.setCar(car);
        }

        if (entity.getImpactSpeed() != null && entity.getImpactSpeed() > 345) {
            throw new IllegalArgumentException("Impact speed cannot exceed 345");
        }

        HumanBeing saved = humanBeingRepository.save(entity);
        return mapper.toDTO(saved);
    }

    public List<Car> getAllCars() {
        return carRepository.findAll();
    }

    public List<Coordinates> getAllCoordinates() {
        return coordinatesRepository.findAll();
    }
}