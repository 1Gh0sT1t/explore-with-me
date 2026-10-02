package ru.practicum.ewm.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import ru.practicum.ewm.dto.CompilationDto;
import ru.practicum.ewm.dto.EventShortDto;
import ru.practicum.ewm.dto.NewCompilationDto;
import ru.practicum.ewm.dto.UpdateCompilationRequest;
import ru.practicum.ewm.exception.NotFoundException;
import ru.practicum.ewm.mapper.CompilationMapper;
import ru.practicum.ewm.model.Compilation;
import ru.practicum.ewm.model.Event;
import ru.practicum.ewm.repository.CompilationRepository;
import ru.practicum.ewm.repository.EventRepository;
import ru.practicum.ewm.service.CompilationService;
import ru.practicum.ewm.service.EventDtoAssembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CompilationServiceImpl implements CompilationService {

    private final CompilationRepository compilationRepository;
    private final EventRepository eventRepository;
    private final CompilationMapper compilationMapper;
    private final EventDtoAssembler eventDtoAssembler;

    @Override
    public CompilationDto addCompilation(NewCompilationDto newCompilationDto) {
        Compilation compilation = compilationMapper.toEntity(newCompilationDto);
        compilation.setPinned(newCompilationDto.getPinned());
        compilation.setEvents(newCompilationDto.getEvents() == null
                ? new ArrayList<>()
                : eventRepository.findAllById(newCompilationDto.getEvents()));
        return toDtos(List.of(compilationRepository.save(compilation))).getFirst();
    }

    @Override
    public void deleteCompilation(Long compId) {
        if (!compilationRepository.existsById(compId)) {
            throw new NotFoundException("Compilation with id=" + compId + " was not found");
        }
        compilationRepository.deleteById(compId);
    }

    @Override
    public CompilationDto updateCompilation(Long compId, UpdateCompilationRequest updateRequest) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
        if (updateRequest.getTitle() != null) {
            compilation.setTitle(updateRequest.getTitle());
        }
        if (updateRequest.getPinned() != null) {
            compilation.setPinned(updateRequest.getPinned());
        }
        if (updateRequest.getEvents() != null) {
            compilation.setEvents(eventRepository.findAllById(updateRequest.getEvents()));
        }
        return toDtos(List.of(compilationRepository.save(compilation))).getFirst();
    }

    @Override
    public List<CompilationDto> getCompilations(Boolean pinned, Pageable pageable) {
        List<Compilation> compilations = pinned == null
                ? compilationRepository.findAll(pageable).getContent()
                : compilationRepository.findAllByPinned(pinned, pageable).getContent();
        if (compilations.isEmpty()) {
            return List.of();
        }
        List<Long> ids = compilations.stream().map(Compilation::getId).toList();
        Map<Long, Compilation> loaded = compilationRepository.findAllWithEventsByIdIn(ids).stream()
                .collect(Collectors.toMap(Compilation::getId, Function.identity()));
        return toDtos(ids.stream().map(loaded::get).toList());
    }

    @Override
    public CompilationDto getCompilation(Long compId) {
        Compilation compilation = compilationRepository.findById(compId)
                .orElseThrow(() -> new NotFoundException("Compilation with id=" + compId + " was not found"));
        return toDtos(List.of(compilation)).getFirst();
    }

    private List<CompilationDto> toDtos(List<Compilation> compilations) {
        List<Event> events = compilations.stream()
                .flatMap(compilation -> compilation.getEvents().stream())
                .distinct()
                .toList();
        Map<Long, EventShortDto> eventDtos = eventDtoAssembler.toShortDtos(events).stream()
                .collect(Collectors.toMap(EventShortDto::getId, Function.identity()));

        return compilations.stream()
                .map(compilation -> {
                    CompilationDto dto = compilationMapper.toDto(compilation);
                    dto.setEvents(compilation.getEvents().stream()
                            .map(event -> eventDtos.get(event.getId()))
                            .toList());
                    return dto;
                })
                .toList();
    }
}
