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
package com.axelor.studio.web;

import java.util.ArrayList;
import java.util.List;

import com.axelor.common.Inflector;
import com.axelor.meta.db.MetaView;
import com.axelor.meta.db.repo.MetaViewRepository;
import com.axelor.rpc.ActionRequest;
import com.axelor.rpc.ActionResponse;
import com.axelor.studio.db.ActionBuilder;
import com.axelor.studio.db.ActionBuilderView;
import com.google.inject.Inject;

public class ActionBuilderController {
	
	@Inject
	private MetaViewRepository metaViewRepo;
	
	private Inflector inflector;
	
	public void setViews(ActionRequest request, ActionResponse response) {
		
		inflector = Inflector.getInstance();
		
		ActionBuilder builder = request.getContext().asType(ActionBuilder.class);
		String model = builder.getModel();
		
		if (builder.getTypeSelect() == 3 && model != null) {
			if (!builder.getIsJson()) {
				model = model.substring(model.lastIndexOf(".") + 1);
				model = inflector.dasherize(model);
			}
			List<ActionBuilderView> views = new ArrayList<ActionBuilderView>();
			addActionBuilderView(views, model, "grid", builder.getIsJson());
			addActionBuilderView(views, model, "form", builder.getIsJson());
			response.setValue("actionBuilderViews", views);
		}
		
	}

	private void addActionBuilderView(List<ActionBuilderView> views, String model, String type, boolean isJson) {
		
		String viewName = model + "-" + type;
		if (isJson) {
			viewName = "custom-model-" + model + "-" + type;
		}
		
		MetaView view = metaViewRepo.findByName(viewName);
		if (view == null) {
			return;
		}
		
		ActionBuilderView builderView = new ActionBuilderView();
		builderView.setViewName(view.getName());
		builderView.setViewType(view.getType());
		
		views.add(builderView);
		
	}
	
}