package eu.dietwise.dao.jpa.interventions;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "DW_INTERVENTION")
public class InterventionEntity {
	@Id
	@Column(name = "id")
	private Integer id;

	@Column(name = "active")
	private Boolean active;

	@Column(name = "text_en")
	private String textEn;

	@Column(name = "text_nl")
	private String textNl;

	@Column(name = "text_el")
	private String textEl;

	@Column(name = "text_lt")
	private String textLt;

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Boolean getActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public String getTextEn() {
		return textEn;
	}

	public void setTextEn(String textEn) {
		this.textEn = textEn;
	}

	public String getTextNl() {
		return textNl;
	}

	public void setTextNl(String textNl) {
		this.textNl = textNl;
	}

	public String getTextEl() {
		return textEl;
	}

	public void setTextEl(String textEl) {
		this.textEl = textEl;
	}

	public String getTextLt() {
		return textLt;
	}

	public void setTextLt(String textLt) {
		this.textLt = textLt;
	}
}
