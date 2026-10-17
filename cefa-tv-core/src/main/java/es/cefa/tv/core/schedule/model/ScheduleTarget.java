package es.cefa.tv.core.schedule.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Destino de una programación: un reproductor o un grupo. */
@Entity
@Table(name = "schedule_targets")
@Getter
@Setter
@NoArgsConstructor
public class ScheduleTarget {

  @Id private UUID id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "schedule_id")
  private Schedule schedule;

  @Column(name = "player_id")
  private UUID playerId;

  @Column(name = "group_id")
  private UUID groupId;

  @Column(name = "site_id")
  private UUID siteId;
}
