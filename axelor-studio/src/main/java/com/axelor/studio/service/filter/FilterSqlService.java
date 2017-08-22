/**
 * Axelor Business Solutions
 *
 * Copyright (C) 2016 Axelor (<http://axelor.com>).
 *
 * This program is free software: you can redistribute it and/or  modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.axelor.studio.service.filter;

import java.lang.invoke.MethodHandles;
import java.util.List;

import org.hibernate.internal.SessionImpl;
import org.hibernate.persister.entity.AbstractEntityPersister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.axelor.common.Inflector;
import com.axelor.db.JPA;
import com.axelor.db.mapper.Mapper;
import com.axelor.exception.AxelorException;
import com.axelor.meta.db.MetaField;
import com.axelor.meta.db.MetaJsonField;
import com.axelor.meta.db.MetaJsonModel;
import com.axelor.meta.db.MetaJsonRecord;
import com.axelor.meta.db.MetaModel;
import com.axelor.meta.db.repo.MetaFieldRepository;
import com.axelor.meta.db.repo.MetaJsonFieldRepository;
import com.axelor.meta.db.repo.MetaModelRepository;
import com.axelor.studio.db.Filter;
import com.google.inject.Inject;

public class FilterSqlService {
	
	private final Logger log = LoggerFactory.getLogger( MethodHandles.lookup().lookupClass() );
	
	@Inject
	private FilterCommonService filterCommonService;
	
	@Inject
	private MetaFieldRepository metaFieldRepo;
	
	@Inject
	private MetaModelRepository metaModelRepo;
	
	@Inject
	private MetaJsonFieldRepository metaJsonFieldRepo;
	
	
	public String getColumn(String model, String field) {
		
		SessionImpl sessionImpl = (SessionImpl) JPA.em().getDelegate();
		AbstractEntityPersister aep=((AbstractEntityPersister)sessionImpl
					.getSession().getSessionFactory().getClassMetadata(model));
		String[] columns = aep.getPropertyColumnNames(field);
		if (columns != null && columns.length > 0) {
			return columns[0];
		}
		
		return null;
	}
	
	public String getColumn(MetaField metaField) {
		
		return getColumn(metaField.getMetaModel().getFullName(), metaField.getName());
	}
	
	public String getSqlType(String type) {
		
		switch (type) {
		case "string":
			return "varchar";
		}
		
		return type;
	}
	
	public String getSqlFilters(List<Filter> filterList, List<String> joins, boolean checkJson) throws AxelorException {
		
		String filters = null;

		if (filterList == null) {
			return filters;
		}
		
		for (Filter filter : filterList) {

			StringBuilder parent = new StringBuilder("self");
			Object target = getTargetField(parent, filter, joins, checkJson);
			if (target == null) {
				continue;
			}
			String field = getSqlField(target, parent.toString())[0];
			String value = getParam(filter.getIsParameter(), filter.getValue(), filter.getId());
			String condition = filterCommonService.getCondition(field, filter.getOperator(), value);
			
			if (filters == null) {
				filters = condition;
			} else {
				String opt = filter.getLogicOp() == 0 ? " AND " : " OR ";
				filters = filters + opt + condition;
			}
		}
		
		return filters;

	}
	

	public String[] getSqlField(Object target, String source) {
		
		String field = null;
		String type = null;
		
		if (target instanceof MetaField) {
			MetaField metaField = (MetaField)target;
			field = source + "." + getColumn(metaField);
			type = metaField.getTypeName();
		}
		else {
			MetaJsonField metaJsonField = (MetaJsonField)target;
			String jsonColumn = getColumn(metaJsonField.getModel(), metaJsonField.getModelField());
			field = "cast(" + source + "." + jsonColumn + "->>'"
					+ metaJsonField.getName() 
					+ "' as " + getSqlType(metaJsonField.getType()) + ")";
			type = metaJsonField.getType();
		}
		
		return new String[]{field, type};
	}
	
	private String getParam(boolean isParam, String value, Long filterId)  {
		
		if (isParam) {
			value = ":param" + filterId;
		}
		
		return value;
	}

	public String[] getDefaultTarget(String fieldName, String modelName) {
		
		MetaModel targetModel = null;
		if (modelName.contains(".")) {
			targetModel = metaModelRepo.all().filter("self.fullName = ?1", modelName).fetchOne();
		}
		else {
			targetModel = metaModelRepo.findByName(modelName);
		}
		
		if (targetModel == null) {
			return new String[]{fieldName, null};
		}
		
		try {
			Mapper mapper = Mapper.of(Class.forName(targetModel.getFullName()));
			if (mapper.getNameField() != null) {
				return new String[]{fieldName + "." + mapper.getNameField().getName(),
							mapper.getNameField().getJavaType().getSimpleName()};
			}
		} catch (ClassNotFoundException e) {
			e.printStackTrace();
		}
		
		for (MetaField field : targetModel.getMetaFields()) {
			if (field.getName().equals("name")) {
				return new String[]{fieldName + ".name", field.getTypeName()};
			}
			if (field.getName().equals("code")) {
				return new String[]{fieldName + ".code", field.getTypeName()};
			}
		}
		
		return new String[]{fieldName, null};
	}
	
	public String[] getDefaultTargetJson(String fieldName, MetaJsonModel targetModel) {
		
		String name = targetModel.getNameField();
		if (name == null) {
			MetaJsonField nameField = metaJsonFieldRepo.all().filter("self.name = 'name' and self.jsonModel = ?1", targetModel).fetchOne();
			if (nameField == null) {
				nameField = metaJsonFieldRepo.all().filter("self.name = 'code' and self.jsonModel = ?1", targetModel).fetchOne();
			}
			if (nameField != null) {
				name = nameField.getName();
			}
		}
		return new String[]{fieldName + "." +  name, "string"};
	}
	
	
	public Object getTargetField(StringBuilder parent, Filter filter, 
			List<String> joins, boolean checkJson) throws AxelorException {
		
		Object target = null;
		
		if (!filter.getIsJson() && filter.getMetaField() != null){
			target = parseMetaField(filter.getMetaField(), filter.getTargetField(), 
					joins, parent, checkJson);
		} else if (checkJson && filter.getMetaJsonField() != null){
			target = parseJsonField(filter.getMetaJsonField(), filter.getTargetField(),
					joins, parent);
		}
		
		return target;
	}
	
	public String getTargetType(Object target) {
		
		String targetType = null;
		if (target instanceof MetaField) {
			MetaField metaField = (MetaField)target;
			String relationship = metaField.getRelationship();
			if (relationship != null) {
				targetType = relationship;
			} else {
				targetType = metaField.getTypeName();
			}
		}
		else if (target instanceof MetaJsonField) {
			targetType = ((MetaJsonField)target).getType();
			log.debug("Target json type:", targetType);
			targetType = Inflector.getInstance().camelize(targetType);
		}
		
		log.debug("Target type: {}, field: {}", targetType, target);
		
		return targetType;
	}

	public Object parseMetaField(MetaField field, String target, List<String> joins, 
			StringBuilder parent, boolean checkJson) throws AxelorException {
		
		if (target == null || !target.contains(".")) {
			if (field.getRelationship() != null && joins != null) {
				target = getDefaultTarget(field.getName(), field.getTypeName())[0];
			}
			else {
				return field;
			}
		}	
		
		target = target.substring(target.indexOf(".") + 1);
		String targetName = target.contains(".") ? target.substring(0,target.indexOf(".")) : target;
		if (field.getRelationship() == null) {
			return field;
		}
		
		MetaModel model = metaModelRepo.findByName(field.getTypeName());
		MetaField subMeta = findMetaField(targetName, model.getFullName());
		if (subMeta != null) {
			if (joins != null) { addJoin(field, joins, parent); }
			return parseMetaField(subMeta, target, joins, parent, checkJson);
		}
		else if (checkJson)  {
			MetaJsonField subJson = findJsonField(targetName, model.getName());
			if (subJson != null) {
				if (joins != null) { addJoin(field, joins, parent); }
				return parseJsonField(subJson, target, joins, parent);
			}
		}
		
		throw new AxelorException("No sub field found field: %s model: %s ", 
				1, targetName, model.getFullName());
		
	}
	
	public Object parseJsonField(MetaJsonField field, String target, List<String> joins,
			StringBuilder parent) throws AxelorException {
		
		log.debug("Parse json target: {}", target);
		
		if (target == null || !target.contains(".")) {
			if (field.getTargetJsonModel() != null && joins != null) {
				target = getDefaultTargetJson(field.getName(), field.getTargetJsonModel())[0];
			}
			else if (field.getTargetModel() != null && joins != null) {
				target = getDefaultTarget(field.getName(), field.getTargetModel())[0];
			}
			else {
				return field;
			}
		}
		
		target = target.substring(target.indexOf(".") + 1);
		
		String targetName = target.contains(".") ? target.substring(0,target.indexOf(".")) : target;
		if (field.getTargetJsonModel() == null && field.getTargetModel() == null) {
			return field;
		}
		
		if (field.getTargetJsonModel() != null) {
			MetaJsonField subJson = metaJsonFieldRepo.all()
					.filter("self.name = ?1 and self.jsonModel = ?2",
							targetName, field.getTargetJsonModel()).fetchOne();
			if (subJson != null) {
				if (joins != null) { addJoin(field, joins, parent); }
				return parseJsonField(subJson, target, joins, parent);
			}
			throw new AxelorException("No sub field found model: %s field %s ", 1,
					field.getTargetJsonModel().getName(), targetName);
		}
		else {
			MetaField subMeta = findMetaField(targetName, field.getTargetModel());
			if (subMeta != null) {
				if (joins != null) { addJoin(field, joins, parent); }
				return parseMetaField(subMeta, target, joins, parent, true);
			}
			throw new AxelorException("No sub field found model: %s field %s ", 1,
					field.getTargetModel(), targetName);
		}
		
	}
	
	private MetaField findMetaField(String name, String model) {
		
		return metaFieldRepo.all()
				.filter("self.name = ?1 and self.metaModel.fullName = ?2",
						name, model).fetchOne();
		
	}
	
	private MetaJsonField findJsonField(String name, String model) {
		
		return metaJsonFieldRepo.all()
				.filter("self.name = ?1 and self.model = ?2",
						name, model).fetchOne();
		
	}

	private void addJoin(MetaField field, List<String> joins, StringBuilder parent) {
		
		MetaModel metaModel = metaModelRepo.findByName(field.getTypeName());
		String parentField = getColumn(field);
		joins.add("left join " + metaModel.getTableName() + " " 
					+ "obj" + joins.size() + " on (" 
					+ "obj" + joins.size() + ".id = " 
					+ parent.toString() + "." + parentField + ")");
		parent.replace(0, parent.length(), "obj" + (joins.size() - 1));
	}
	
	private void addJoin(MetaJsonField field, List<String> joins , StringBuilder parent) {
		
		String targetModel = null;
		if (field.getTargetModel() != null) {
			targetModel = field.getTargetModel();
		}
		else if (field.getTargetJsonModel() != null) {
			targetModel = MetaJsonRecord.class.getName();
		}
		
		MetaModel metaModel = metaModelRepo.all()
				.filter("self.fullName = ?1", targetModel).fetchOne();
		
		joins.add("left join " + metaModel.getTableName() + " " 
				+ "obj" + joins.size() + " on (obj" + joins.size() + ".id = " 
				+ "cast(" + parent  + "." + getColumn(field.getModel(), field.getModelField()) 
				+ "->'" + field.getName() +  "'->>'id' as integer))");
		
		parent.replace(0, parent.length(), "obj" + (joins.size() - 1));
	}

}