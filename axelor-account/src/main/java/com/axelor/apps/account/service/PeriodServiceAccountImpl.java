/*
 * Axelor Business Solutions
 *
 * Copyright (C) 2018 Axelor (<http://axelor.com>).
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
package com.axelor.apps.account.service;

import com.axelor.apps.account.db.Move;
import com.axelor.apps.account.db.repo.MoveRepository;
import com.axelor.apps.account.service.move.MoveValidateService;
import com.axelor.apps.base.db.Period;
import com.axelor.apps.base.db.repo.PeriodRepository;
import com.axelor.apps.base.db.repo.YearRepository;
import com.axelor.apps.base.service.AdjustHistoryService;
import com.axelor.apps.base.service.PeriodServiceImpl;
import com.axelor.exception.AxelorException;
import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import java.util.List;
import javax.inject.Singleton;

@Singleton
public class PeriodServiceAccountImpl extends PeriodServiceImpl {

  protected MoveValidateService moveValidateService;
  protected MoveRepository moveRepository;

  @Inject
  public PeriodServiceAccountImpl(
      PeriodRepository periodRepo,
      AdjustHistoryService adjustHistoryService,
      MoveValidateService moveValidateService,
      MoveRepository moveRepository) {
    super(periodRepo, adjustHistoryService);
    this.moveValidateService = moveValidateService;
    this.moveRepository = moveRepository;
  }

  @Transactional(rollbackOn = {AxelorException.class, Exception.class})
  public void close(Period period) {

    if (period.getYear().getTypeSelect() == YearRepository.TYPE_FISCAL) {
      moveValidateService.validateMultiple(getMoveListToValidate(period));
    }

    super.close(period);
  }

  public List<Move> getMoveListToValidate(Period period) {

    return moveRepository
        .all()
        .filter(
            "self.period.id = ?1 AND self.statusSelect NOT IN (?2, ?3)",
            period.getId(),
            MoveRepository.STATUS_VALIDATED,
            MoveRepository.STATUS_CANCELED)
        .order("date")
        .fetch();
  }
}
