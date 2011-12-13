package de.sub.goobi.beans;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.goobi.production.api.property.xmlbasedprovider.Status;

import de.sub.goobi.beans.property.IGoobiEntity;
import de.sub.goobi.beans.property.IGoobiProperty;
import de.sub.goobi.helper.enums.PropertyType;

public class Werkstueckeigenschaft implements Serializable, IGoobiProperty {
	private static final long serialVersionUID = -88407008893258729L;
	private Werkstueck werkstueck;
	private Integer id;
	private String titel;
	private String wert;
	private Boolean istObligatorisch;
	private Integer datentyp;
	private String auswahl;
	private Date creationDate;
	private Integer container;

	public Werkstueckeigenschaft() {
		istObligatorisch = false;
		datentyp = PropertyType.String.getId();
		creationDate = new Date();
	}

	private List<String> valueList;

	public String getAuswahl() {
		return auswahl;
	}

	public void setAuswahl(String auswahl) {
		this.auswahl = auswahl;
	}

	public Integer getId() {
		return id;
	}

	public void setId(Integer id) {
		this.id = id;
	}

	public Boolean isIstObligatorisch() {
		if (istObligatorisch == null) {
			istObligatorisch = false;
		}
		return istObligatorisch;
	}

	public void setIstObligatorisch(Boolean istObligatorisch) {
		this.istObligatorisch = istObligatorisch;
	}

	public String getTitel() {
		return titel;
	}

	public void setTitel(String titel) {
		this.titel = titel;
	}

	public String getWert() {
		return wert;
	}

	public void setWert(String wert) {
		this.wert = wert;
	}

	public void setCreationDate(Date creation) {
		this.creationDate = creation;
	}

	public Date getCreationDate() {
		return creationDate;
	}

	/**
	 * getter for datentyp set to private for hibernate
	 * 
	 * for use in programm use getType instead
	 * 
	 * @return datentyp as integer
	 */
	@SuppressWarnings("unused")
	private Integer getDatentyp() {
		return datentyp;
	}

	/**
	 * set datentyp to defined integer. only for internal use through hibernate, for changing datentyp use setType instead
	 * 
	 * @param datentyp
	 *            as Integer
	 */
	@SuppressWarnings("unused")
	private void setDatentyp(Integer datentyp) {
		this.datentyp = datentyp;
	}

	/**
	 * set datentyp to specific value from {@link PropertyType}
	 * 
	 * @param inType
	 *            as {@link PropertyType}
	 */
	public void setType(PropertyType inType) {
		this.datentyp = inType.getId();
	}

	/**
	 * get datentyp as {@link PropertyType}
	 * 
	 * @return current datentyp
	 */
	public PropertyType getType() {
		if (datentyp == null) {
			datentyp = PropertyType.String.getId();
		}
		return PropertyType.getById(datentyp);
	}

	public List<String> getValueList() {
		if (valueList == null) {
			valueList = new ArrayList<String>();
		}
		return valueList;
	}

	public void setValueList(List<String> valueList) {
		this.valueList = valueList;
	}

	public Werkstueck getWerkstueck() {
		return werkstueck;
	}

	public void setWerkstueck(Werkstueck werkstueck) {
		this.werkstueck = werkstueck;
	}

	public Status getStatus() {
		return Status.getProductStatusFromEntity(werkstueck);
	}

	public IGoobiEntity getOwningEntity() {
		return werkstueck;
	}
	public void setOwningEntity(IGoobiEntity inEntity) {
		this.werkstueck = (Werkstueck) inEntity;	
	}
	
	public Integer getContainer() {
		if (container == null) {
			return 0;
		}
		return container;
	}

	public void setContainer(Integer order) {
		if (order == null) {
			order = 0;
		}
		this.container = order;
	}
	
	
	@Override
	public String getNormalizedTitle() {
		return titel.replace(" ", "_").trim();
	}

	@Override
	public String getNormalizedValue() {
		return wert.replace(" ", "_").trim();
	}
}
