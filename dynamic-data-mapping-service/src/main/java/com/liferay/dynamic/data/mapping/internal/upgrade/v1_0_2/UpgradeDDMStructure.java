/**
 * Copyright (c) 2000-present Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 */

package com.liferay.dynamic.data.mapping.internal.upgrade.v1_0_2;

import com.liferay.dynamic.data.mapping.expression.DDMExpression;
import com.liferay.dynamic.data.mapping.expression.DDMExpressionFactory;
import com.liferay.dynamic.data.mapping.expression.VariableDependencies;
import com.liferay.dynamic.data.mapping.io.DDMFormJSONDeserializer;
import com.liferay.dynamic.data.mapping.io.DDMFormJSONSerializer;
import com.liferay.dynamic.data.mapping.model.DDMForm;
import com.liferay.dynamic.data.mapping.model.DDMFormField;
import com.liferay.dynamic.data.mapping.model.DDMFormRule;
import com.liferay.portal.kernel.dao.jdbc.AutoBatchPreparedStatementUtil;
import com.liferay.portal.kernel.upgrade.UpgradeProcess;
import com.liferay.portal.kernel.util.StringBundler;
import com.liferay.portal.kernel.util.StringUtil;
import com.liferay.portal.kernel.util.Validator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;

import java.util.List;
import java.util.Map;

/**
 * @author Inácio Nery
 */
public class UpgradeDDMStructure extends UpgradeProcess {

	public UpgradeDDMStructure(
		DDMExpressionFactory ddmExpressionFactory,
		DDMFormJSONDeserializer ddmFormJSONDeserializer,
		DDMFormJSONSerializer ddmFormJSONSerializer) {

		_ddmExpressionFactory = ddmExpressionFactory;
		_ddmFormJSONDeserializer = ddmFormJSONDeserializer;
		_ddmFormJSONSerializer = ddmFormJSONSerializer;
	}

	@Override
	protected void doUpgrade() throws Exception {
		StringBundler sb = new StringBundler(2);

		sb.append("select DDMStructure.definition, DDMStructure.structureId ");
		sb.append("from DDMStructure");

		try (PreparedStatement ps1 = connection.prepareStatement(sb.toString());
			PreparedStatement ps2 =
				AutoBatchPreparedStatementUtil.concurrentAutoBatch(
					connection,
					"update DDMStructure set definition = ? where " +
						"structureId = ?")) {

			try (ResultSet rs = ps1.executeQuery()) {
				while (rs.next()) {
					String definition = rs.getString(1);
					long structureId = rs.getLong(2);

					DDMForm ddmForm = _ddmFormJSONDeserializer.deserialize(
						definition);

					List<DDMFormField> ddmFormFields =
						ddmForm.getDDMFormFields();

					List<DDMFormRule> ddmFormRules = ddmForm.getDDMFormRules();

					for (DDMFormField ddmFormField : ddmFormFields) {
						String visibilityExpression =
							ddmFormField.getVisibilityExpression();

						if (Validator.isNull(visibilityExpression)) {
							continue;
						}

						DDMExpression<Boolean> ddmExpression =
							_ddmExpressionFactory.createBooleanDDMExpression(
								visibilityExpression);

						Map<String, VariableDependencies> variableDependencies =
							ddmExpression.getVariableDependenciesMap();

						for (String variable : variableDependencies.keySet()) {
							visibilityExpression = StringUtil.replace(
								visibilityExpression, new String[] {variable},
								new String[] {
									"getValue(" + StringUtil.quote(variable) +
										")"
								},
								true);
						}

						DDMFormRule ddmFormRule = new DDMFormRule(
							visibilityExpression,
							"setVisible('" + ddmFormField.getName() +
								"', true)");

						ddmFormRules.add(ddmFormRule);

						ddmFormField.setVisibilityExpression("");
					}

					ddmForm.setDDMFormRules(ddmFormRules);

					String newDefinition = _ddmFormJSONSerializer.serialize(
						ddmForm);

					ps2.setString(1, newDefinition);

					ps2.setLong(2, structureId);

					ps2.addBatch();
				}

				ps2.executeBatch();
			}
		}
	}

	private final DDMExpressionFactory _ddmExpressionFactory;
	private final DDMFormJSONDeserializer _ddmFormJSONDeserializer;
	private final DDMFormJSONSerializer _ddmFormJSONSerializer;

}