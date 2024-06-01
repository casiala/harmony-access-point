import {Component, OnInit} from '@angular/core';
import {AlertService} from '../common/alert/alert.service';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs/Observable';
import {MessageFilterResult} from './support/messagefilterresult';
import {BackendFilterEntry} from './support/backendfilterentry';
import {RoutingCriteriaEntry} from './support/routingcriteriaentry';
import {EditMessageFilterComponent} from './editmessagefilter-form/editmessagefilter-form.component';
import mix from '../common/mixins/mixin.utils';
import BaseListComponent from '../common/mixins/base-list.component';
import ModifiableListMixin from '../common/mixins/modifiable-list.mixin';
import {ApplicationContextService} from '../common/application-context.service';
import {ComponentName} from '../common/component-name-decorator';
import {ManageBackendsComponent} from './manageBackends-form/manageBackends-form.component';
import {DialogsService} from '../common/dialogs/dialogs.service';
import {SelectionType} from '@swimlane/ngx-datatable';

@Component({
  templateUrl: 'messagefilter.component.html',
  providers: [],
  styleUrls: ['./messagefilter.component.css']
})
@ComponentName('Message Filters')
export class MessageFilterComponent extends mix(BaseListComponent).with(ModifiableListMixin)
  implements OnInit {

  static readonly MESSAGE_FILTER_URL: string = 'rest/messagefilters';

  backendConnectors: { name: string, active: boolean, enabledPropertyName: string }[];
  rowNumber: number;
  areFiltersPersisted: boolean;

  enableSave: boolean;

  constructor(private applicationService: ApplicationContextService, private http: HttpClient, private alertService: AlertService,
              protected dialogsService: DialogsService) {
    super();
  }

  ngOnInit() {
    super.ngOnInit();

    this.backendConnectors = [];
    this.rowNumber = -1;
    this.loadServerData();
  }

  async getDataAndSetResults(): Promise<any> {
    this.getBackendFiltersInfo();
  }

  getBackendFiltersInfo() {
    this.disableSelectionAndButtons();
    return this.getMessageFilterEntries().toPromise().then((result: MessageFilterResult) => {
      const newRows = [];
      this.backendConnectors = [];
      if (result.messageFilterEntries) {
        for (let i = 0; i < result.messageFilterEntries.length; i++) {
          let currentFilter = result.messageFilterEntries[i];
          if (!(currentFilter)) {
            continue;
          }
          let backendEntry = new BackendFilterEntry(currentFilter.entityId, i, currentFilter.backendName, currentFilter.routingCriterias,
            currentFilter.persisted, currentFilter.active, currentFilter.enabledPropertyName);
          newRows.push(backendEntry);
          if (!this.backendConnectors.some(el => el.name == backendEntry.backendName)) {
            this.backendConnectors.push({
              name: backendEntry.backendName,
              active: backendEntry.active,
              enabledPropertyName: backendEntry.enabledPropertyName
            });
          }
        }
        this.areFiltersPersisted = result.areFiltersPersisted;

        super.rows = newRows;
        super.count = newRows.length;

        super.isChanged = false;

        if (!this.areFiltersPersisted && this.backendConnectors.length > 1) {
          this.alertService.error('One or several filters in the table were not configured yet (Persisted flag is not checked). ' +
            'It is strongly recommended to double check the filters configuration and afterwards save it.');
          this.enableSave = true;
        }
      }
    });
  }

  getMessageFilterEntries(): Observable<MessageFilterResult> {
    return this.http.get<MessageFilterResult>(MessageFilterComponent.MESSAGE_FILTER_URL);
  }

  async add() {
    if (this.isBusy()) {
      return;
    }

    let activeConnectors = this.backendConnectors.filter(bConn => bConn.active);
    let activeConnector = activeConnectors[0];
    let backendEntry = new BackendFilterEntry(0, this.rows.length + 1, activeConnector.name, [], false,
      activeConnector.active, null);
    const ok = await this.dialogsService.openAndThen(EditMessageFilterComponent, {
      data: {
        backendConnectors: activeConnectors,
        entity: backendEntry
      }
    });
    if (!ok) {
      return;
    }
    if (this.findRowLike(backendEntry) == -1) {
      super.rows = [...this.rows, backendEntry];
      super.count = this.rows.length + 1;

      this.setDirty(true);
    } else {
      this.alertService.error('Impossible to insert a duplicate entry');
    }

  }

  async edit(row?) {
    row = row || this.selected[0];

    if (!row.active) {
      this.alertService.error('Cannot edit a backend filter of a disabled backend connector.');
      return;
    }

    const backendEntry = JSON.parse(JSON.stringify(row));
    const ok = await this.dialogsService.openAndThen(EditMessageFilterComponent, {
      data: {
        backendConnectors: this.backendConnectors,
        entity: backendEntry
      }
    })
    if (!ok) {
      return;
    }

    let backendEntryPos = this.findRowLike(backendEntry);
    if (backendEntryPos == -1) {
      this.rows.splice(this.rowNumber, 1, backendEntry);
      super.rows = [...this.rows];
      super.count = this.rows.length;

      this.setDirty(true);
    } else {
      if (backendEntryPos != this.rowNumber) {
        this.alertService.error('Impossible to insert a duplicate entry');
      }
    }

    window.setTimeout(() => {
      document.getElementById('pluginRow' + (this.rowNumber) + '_id').click();
    }, 50);

  }

  get csvUrl(): string {
    return MessageFilterComponent.MESSAGE_FILTER_URL + '/csv';
  }

  async doSave(): Promise<any> {
    return this.http.put(MessageFilterComponent.MESSAGE_FILTER_URL, this.rows).toPromise().then(res => {
      this.getBackendFiltersInfo();
    });
  }

  buttonDeleteAction(row) {
    this.deleteItems([row]);
  }

  delete() {
    this.deleteItems(this.selected);
  }

  private deleteItems(items: any[]) {
    this.setDirty(true);

    let copy = [...this.rows];
    // we need to use the old for loop approach to don't mess with the entries on the top before
    for (let i = items.length - 1; i >= 0; i--) {
      let rowIndex = copy.indexOf(items[i]);
      copy.splice(rowIndex, 1);
    }
    super.rows = copy;
    super.count = copy.length;
    super.selected = [];
  }

  buttonMoveUp() {
    this.moveAction(this.selected[0], -1);
  }

  moveAction(row, step: number = 1 || -1) {
    let rowIndex = this.rows.indexOf(row);
    this.moveInternal(rowIndex, step);
    window.setTimeout(() => {
      rowIndex = this.rows.indexOf(row);
      document.getElementById('pluginRow' + (rowIndex) + '_id').click();
    }, 50);
  }

  private moveInternal(rowNumber, step: number = -1 || 1) {
    if ((step == -1 && rowNumber < 1) || (step == 1 && rowNumber > this.rows.length - 1)) {
      return;
    }

    const array = this.rows.slice();
    const move = array[rowNumber];
    array[rowNumber] = array[rowNumber + step];
    array[rowNumber + step] = move;

    super.rows = array.slice();
    super.count = this.rows.length;
    this.rowNumber = this.rowNumber + step;

    this.setDirty(true);
  }

  buttonMoveDown() {
    this.moveAction(this.selected[0], 1);
  }

  onSelect({selected}) {
    this.rowNumber = this.rows.indexOf(this.selected[0]);
  }

  canMoveUp(): boolean {
    return this.oneRowSelected() && this.selected[0].active && this.rowNumber > 0 && !this.isBusy();
  }

  canMoveDown(): boolean {
    return this.oneRowSelected() && this.selected[0].active && this.rowNumber < this.rows.length - 1 && !this.isBusy();
  }

  setDirty(dirty: boolean) {
    super.isChanged = this.isChanged || dirty;
    this.enableSave = this.isChanged;
  }

  canSave() {
    return this.enableSave && !this.isBusy();
  }

  canDelete() {
    return this.selected.length > 0 && !this.isBusy();
  }

  canEdit(): boolean {
    return super.canEdit() && this.selected[0].active;
  }

  private oneRowSelected() {
    return this.selected.length == 1;
  }

  private findRowLike(backendEntry: BackendFilterEntry): number {
    for (let i = 0; i < this.rows.length; i++) {
      let currentRow = this.rows[i];
      if (currentRow.backendName === backendEntry.backendName && this.RoutingCriteriasAreEqual(backendEntry.routingCriterias, currentRow.routingCriterias)) {
        return i;
      }
    }
    return -1;
  }

  private RoutingCriteriasAreEqual(criteriasA: RoutingCriteriaEntry[], criteriasB: RoutingCriteriaEntry[]): boolean {
    let val1 = criteriasA.map(el => el.name + el.expression).join(',');
    let val2 = criteriasB.map(el => el.name + el.expression).join(',');
    return val1 == val2;
  }

  private disableSelectionAndButtons() {
    super.selected = [];
    this.enableSave = false;
  }

  async manageBackends() {
    await this.dialogsService.openAndThen(ManageBackendsComponent, {
      data: {
        backendConnectors: this.backendConnectors,
      }
    });
    this.loadServerData();
  }

  protected readonly SelectionType = SelectionType;
}
