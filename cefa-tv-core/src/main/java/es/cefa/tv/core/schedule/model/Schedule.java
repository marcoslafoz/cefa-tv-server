package es.cefa.tv.core.schedule.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Programación: una playlist emitida en unas fechas, días y franja horaria sobre unos destinos. */
@Entity
@Table(name = "schedules")
@Getter
@Setter
@NoArgsConstructor
public class Schedule {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Column(name = "playlist_id", nullable = false)
  private UUID playlistId;

  @Column(nullable = false)
  private int priority;

  @Column(name = "start_date")
  private LocalDate startDate;

  @Column(name = "end_date")
  private LocalDate endDate;

  @Column(name = "start_time")
  private LocalTime startTime;

  @Column(name = "end_time")
  private LocalTime endTime;

  @JdbcTypeCode(SqlTypes.ARRAY)
  @Column(name = "days_of_week", nullable = false, columnDefinition = "int[]")
  private Integer[] daysOfWeek;

  @Column(name = "all_day", nullable = false)
  private boolean allDay;

  @Column(nullable = false)
  private boolean enabled = true;

  @OneToMany(
      mappedBy = "schedule",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.EAGER)
  private List<ScheduleTarget> targets = new ArrayList<>();
}
