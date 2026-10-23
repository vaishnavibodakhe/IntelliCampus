package com.intellicampus.academic.service;

import com.intellicampus.academic.dto.DepartmentDto;
import com.intellicampus.academic.dto.DivisionDto;
import com.intellicampus.academic.dto.RoomDto;
import com.intellicampus.academic.dto.SubjectDto;
import com.intellicampus.academic.model.Classroom;
import com.intellicampus.academic.model.Department;
import com.intellicampus.academic.model.Division;
import com.intellicampus.academic.model.Subject;
import com.intellicampus.academic.repository.ClassroomRepository;
import com.intellicampus.academic.repository.DepartmentRepository;
import com.intellicampus.academic.repository.DivisionRepository;
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
    private final DivisionRepository divisionRepository;
    private final UserRepository userRepository;

    public AcademicSetupService(ClassroomRepository classroomRepository,
                                SubjectRepository subjectRepository,
                                DepartmentRepository departmentRepository,
                                DivisionRepository divisionRepository,
                                UserRepository userRepository) {
        this.classroomRepository = classroomRepository;
        this.subjectRepository = subjectRepository;
        this.departmentRepository = departmentRepository;
        this.divisionRepository = divisionRepository;
        this.userRepository = userRepository;
    }

    // ==========================================
    // Rooms
    // ==========================================
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

    public RoomDto updateRoom(Long id, RoomDto dto) {
        Classroom room = classroomRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Room not found: " + id));

        room.setName(dto.getName());
        room.setLatitude(dto.getLatitude());
        room.setLongitude(dto.getLongitude());
        if (dto.getRadiusM() != null) room.setRadiusM(dto.getRadiusM());
        if (dto.getCapacity() != null) room.setCapacity(dto.getCapacity());

        if (dto.getDepartmentId() != null) {
            Department dept = departmentRepository.findById(dto.getDepartmentId()).orElse(null);
            room.setDepartment(dept);
        }

        Classroom saved = classroomRepository.save(room);
        return mapRoomToDto(saved);
    }

    public void deleteRoom(Long id) {
        classroomRepository.deleteById(id);
    }

    // ==========================================
    // Subjects
    // ==========================================
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

    public SubjectDto updateSubject(Long id, SubjectDto dto) {
        Subject subject = subjectRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Subject not found: " + id));

        subject.setCode(dto.getCode());
        subject.setName(dto.getName());
        if (dto.getCredits() != null) subject.setCredits(dto.getCredits());

        if (dto.getDepartmentId() != null) {
            departmentRepository.findById(dto.getDepartmentId()).ifPresent(subject::setDepartment);
        }

        if (dto.getFacultyId() != null) {
            userRepository.findById(dto.getFacultyId()).ifPresent(subject::setFaculty);
        }

        Subject saved = subjectRepository.save(subject);
        return mapSubjectToDto(saved);
    }

    public void deleteSubject(Long id) {
        subjectRepository.deleteById(id);
    }

    // ==========================================
    // Departments
    // ==========================================
    @Transactional(readOnly = true)
    public List<DepartmentDto> getAllDepartments() {
        return departmentRepository.findAll().stream()
                .map(d -> new DepartmentDto(d.getId(), d.getCode(), d.getName()))
                .collect(Collectors.toList());
    }

    public DepartmentDto createDepartment(DepartmentDto dto) {
        String code = dto.getCode() != null ? dto.getCode().trim().toUpperCase() : dto.getName().replaceAll("[^A-Za-z0-9]", "_").toUpperCase();
        Department dept = new Department(code, dto.getName());
        Department saved = departmentRepository.save(dept);
        return new DepartmentDto(saved.getId(), saved.getCode(), saved.getName());
    }

    public DepartmentDto updateDepartment(Long id, DepartmentDto dto) {
        Department dept = departmentRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Department not found: " + id));
        dept.setName(dto.getName());
        if (dto.getCode() != null) dept.setCode(dto.getCode());
        Department saved = departmentRepository.save(dept);
        return new DepartmentDto(saved.getId(), saved.getCode(), saved.getName());
    }

    public void deleteDepartment(Long id) {
        departmentRepository.deleteById(id);
    }

    // ==========================================
    // Divisions / Classes
    // ==========================================
    @Transactional(readOnly = true)
    public List<DivisionDto> getAllDivisions() {
        return divisionRepository.findAll().stream()
                .map(d -> new DivisionDto(
                        d.getId(),
                        d.getDepartment() != null ? d.getDepartment().getId() : null,
                        d.getDepartment() != null ? d.getDepartment().getName() : "General",
                        d.getYear(),
                        d.getName()
                ))
                .collect(Collectors.toList());
    }

    public DivisionDto createDivision(DivisionDto dto) {
        Department dept = null;
        if (dto.getDepartmentId() != null) {
            dept = departmentRepository.findById(dto.getDepartmentId()).orElse(null);
        }
        if (dept == null) {
            dept = departmentRepository.findAll().stream().findFirst()
                    .orElseGet(() -> departmentRepository.save(new Department("CS", "Computer Science")));
        }

        Division division = new Division(dept, dto.getYear() != null ? dto.getYear() : 3, dto.getName());
        Division saved = divisionRepository.save(division);
        return new DivisionDto(saved.getId(), dept.getId(), dept.getName(), saved.getYear(), saved.getName());
    }

    public DivisionDto updateDivision(Long id, DivisionDto dto) {
        Division division = divisionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Division not found: " + id));

        division.setName(dto.getName());
        if (dto.getYear() != null) division.setYear(dto.getYear());
        if (dto.getDepartmentId() != null) {
            departmentRepository.findById(dto.getDepartmentId()).ifPresent(division::setDepartment);
        }

        Division saved = divisionRepository.save(division);
        return new DivisionDto(
                saved.getId(),
                saved.getDepartment() != null ? saved.getDepartment().getId() : null,
                saved.getDepartment() != null ? saved.getDepartment().getName() : "General",
                saved.getYear(),
                saved.getName()
        );
    }

    public void deleteDivision(Long id) {
        divisionRepository.deleteById(id);
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
