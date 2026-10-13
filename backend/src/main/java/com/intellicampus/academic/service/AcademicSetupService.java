package com.intellicampus.academic.service;

import com.intellicampus.academic.dto.RoomDto;
import com.intellicampus.academic.dto.SubjectDto;
import com.intellicampus.academic.model.Classroom;
import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Subject;
import com.intellicampus.academic.repository.ClassroomRepository;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.SubjectRepository;
import com.intellicampus.user.model.User;
import com.intellicampus.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class AcademicSetupService {

    private final ClassroomRepository classroomRepository;
    private final SubjectRepository subjectRepository;
    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;

    public AcademicSetupService(ClassroomRepository classroomRepository,
                                SubjectRepository subjectRepository,
                                DepartmentRepository departmentRepository,
                                UserRepository userRepository) {
        this.classroomRepository = classroomRepository;
        this.subjectRepository = subjectRepository;
        this.departmentRepository = departmentRepository;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<RoomDto> getAllRooms() {
        return classroomRepository.findAll().stream()
                .map(this::mapRoomToDto)
                .collect(Collectors.toList());
    }

    public RoomDto createRoom(RoomDto dto) {
        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = departmentRepository.findById(dto.getDepartmentId()).orElse(null);
        }

        Classroom room = new Classroom(
                dto.getName(),
                dto.getLatitude(),
                dto.getLongitude(),
                dto.getRadiusM() != null ? dto.getRadiusM() : 50,
                dto.getCapacity() != null ? dto.getCapacity() : 60,
                dept
        );

        Classroom saved = classroomRepository.save(room);
        return mapRoomToDto(saved);
    }

    @Transactional(readOnly = true)
    public List<SubjectDto> getAllSubjects() {
        return subjectRepository.findAll().stream()
                .map(this::mapSubjectToDto)
                .collect(Collectors.toList());
    }

    public SubjectDto createSubject(SubjectDto dto) {
        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = departmentRepository.findById(dto.getDepartmentId()).orElse(null);
        }
        if (dept == null) {
            dept = departmentRepository.findAll().stream().findFirst()
                    .orElseGet(() -> departmentRepository.save(new Department("CS", "Computer Science")));
        }

        User faculty = null;
        if (dto.getFacultyId() != null) {
            faculty = userRepository.findById(dto.getFacultyId()).orElse(null);
        }

        Subject subject = new Subject(
                dto.getCode(),
                dto.getName(),
                dto.getCredits() != null ? dto.getCredits() : 4,
                dept,
                faculty
        );

        Subject saved = subjectRepository.save(subject);
        return mapSubjectToDto(saved);
    }

    private RoomDto mapRoomToDto(Classroom r) {
        return new RoomDto(
                r.getId(),
                r.getName(),
                r.getLatitude(),
                r.getLongitude(),
                r.getRadiusM(),
                r.getCapacity(),
                r.getDepartment() != null ? r.getDepartment().getId() : null,
                r.getDepartment() != null ? r.getDepartment().getName() : null
        );
    }

    private SubjectDto mapSubjectToDto(Subject s) {
        return new SubjectDto(
                s.getId(),
                s.getCode(),
                s.getName(),
                s.getCredits(),
                s.getDepartment() != null ? s.getDepartment().getId() : null,
                s.getDepartment() != null ? s.getDepartment().getName() : null,
                s.getFaculty() != null ? s.getFaculty().getId() : null,
                s.getFaculty() != null ? s.getFaculty().getName() : null
        );
    }
}
