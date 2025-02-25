import {
  AfterViewChecked,
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  OnInit,
  TemplateRef,
  ViewChild
} from '@angular/core';
import {ColumnPickerBase} from 'app/common/column-picker/column-picker-base';
import {AlertService} from '../common/alert/alert.service';
import {PluginUserSearchCriteria, PluginUserService} from './support/pluginuser.service';
import {PluginUserRO} from './support/pluginuser';
import {EditBasicPluginUserFormComponent} from './editpluginuser-form/edit-basic-plugin-user-form.component';
import {
  EditCertificatePluginUserFormComponent
} from './editpluginuser-form/edit-certificate-plugin-user-form.component';
import {UserService} from '../user/support/user.service';
import {UserState} from '../user/support/user';
import mix from '../common/mixins/mixin.utils';
import BaseListComponent from '../common/mixins/base-list.component';
import FilterableListMixin from '../common/mixins/filterable-list.mixin';
import {DialogsService} from '../common/dialogs/dialogs.service';
import ModifiableListMixin from '../common/mixins/modifiable-list.mixin';
import {ClientPageableListMixin} from '../common/mixins/pageable-list.mixin';
import {HttpClient, HttpParams} from '@angular/common/http';
import {ClientSortableListMixin} from '../common/mixins/sortable-list.mixin';
import {ApplicationContextService} from '../common/application-context.service';
import {ComponentName} from '../common/component-name-decorator';
import {PluginUserValidatorService} from './support/pluginuservalidator.service';
import {SelectionType} from '@swimlane/ngx-datatable';

@Component({
  templateUrl: './pluginuser.component.html',
  styleUrls: ['./pluginuser.component.css'],
  providers: [PluginUserService, UserService]
})
@ComponentName('Plugin Users')
export class PluginUserComponent extends mix(BaseListComponent)
  .with(FilterableListMixin, ClientPageableListMixin, ModifiableListMixin, ClientSortableListMixin)
  implements OnInit, AfterViewInit, AfterViewChecked {

  @ViewChild('activeTpl') activeTpl: TemplateRef<any>;
  @ViewChild('rowActions') rowActions: TemplateRef<any>;
  @ViewChild('rowWithDateFormatTpl') public rowWithDateFormatTpl: TemplateRef<any>;

  columnPickerBasic: ColumnPickerBase = new ColumnPickerBase();
  columnPickerCert: ColumnPickerBase = new ColumnPickerBase();

  authenticationTypes: string[] = ['BASIC', 'CERTIFICATE'];
  filter: PluginUserSearchCriteria;
  columnPicker: ColumnPickerBase = new ColumnPickerBase();

  userRoles: Array<String>;

  constructor(private applicationService: ApplicationContextService, private alertService: AlertService,
              private pluginUserService: PluginUserService, private dialogsService: DialogsService,
              private changeDetector: ChangeDetectorRef, private http: HttpClient, private pluginUserValidatorService: PluginUserValidatorService) {
    super();
  }

  ngOnInit() {
    super.ngOnInit();

    this.filter = {authType: 'BASIC', authRole: '', userName: '', originalUser: ''};

    this.userRoles = [];
    this.getUserRoles();
    this.filterData();
  }

  ngAfterViewInit() {
    this.initColumns();
  }

  ngAfterViewChecked() {
    this.changeDetector.detectChanges();
  }

  get displayedUsers(): PluginUserRO[] {
    return this.rows.filter(el => el.status !== UserState[UserState.REMOVED]);
  }

  private initColumns() {
    this.columnPickerBasic.allColumns = [
      {
        name: 'User Name',
        prop: 'userName',
        width: 200,
        minWidth: 190,
        showInitially: true
      },
      {
        name: 'Role',
        prop: 'authRoles',
        width: 100,
        minWidth: 90,
        showInitially: true
      },
      {
        name: 'Active',
        prop: 'active',
        cellTemplate: this.activeTpl,
        width: 70,
        minWidth: 60,
        showInitially: true
      },
      {
        name: 'Original User',
        prop: 'originalUser',
        width: 200,
        minWidth: 190,
        showInitially: true
      },
      {
        cellTemplate: this.rowWithDateFormatTpl,
        name: 'Expiration Date',
        prop: 'expirationDate',
        canAutoResize: true,
        showInitially: true,
        width: 200,
        minWidth: 190,
      },
      {
        cellTemplate: this.rowActions,
        name: 'Actions',
        width: 100,
        minWidth: 90,
        canAutoResize: true,
        sortable: false,
        showInitially: true
      }
    ];
    this.columnPickerCert.allColumns = [
      {
        name: 'Certificate Id',
        prop: 'certificateId',
        width: 200,
        minWidth: 190,
        showInitially: true
      },
      {
        name: 'Role',
        prop: 'authRoles',
        width: 70,
        minWidth: 60,
        showInitially: true
      },
      {
        name: 'Original User',
        prop: 'originalUser',
        width: 200,
        minWidth: 190,
        showInitially: true
      },
      {
        cellTemplate: this.rowActions,
        name: 'Actions',
        width: 100,
        minWidth: 90,
        canAutoResize: true,
        sortable: false,
        showInitially: true
      }
    ];

    this.columnPickerBasic.selectedColumns = this.columnPickerBasic.allColumns.filter(col => col.showInitially);
    this.columnPickerCert.selectedColumns = this.columnPickerCert.allColumns.filter(col => col.showInitially);

    this.setColumnPicker();
  }

  setColumnPicker() {
    this.columnPicker = this.filter.authType === 'CERTIFICATE' ? this.columnPickerCert : this.columnPickerBasic;
  }

  changeAuthType(x) {
    this.clearSearchParams();

    super.tryFilter(false);
  }

  clearSearchParams() {
    this.filter.authRole = null;
    this.filter.originalUser = null;
    this.filter.userName = null;
  }

  protected get GETUrl(): string {
    return PluginUserService.PLUGIN_USERS_URL;
  }

  protected createAndSetParameters(): HttpParams {
    let filterParams = super.createAndSetParameters();

    filterParams = filterParams.append('page', '0');
    filterParams = filterParams.append('pageSize', '10000');

    return filterParams;
  }

  public async setServerResults(result: { entries: PluginUserRO[], count: number }) {
    await this.pluginUserService.checkConfiguredCorrectlyForMultitenancy(result.entries);

    super.rows = result.entries;
    super.count = result.entries.length;

    this.setColumnPicker();
  }

  inBasicMode(): boolean {
    return this.filter.authType === 'BASIC';
  }

  inCertificateMode(): boolean {
    return this.filter.authType === 'CERTIFICATE';
  }

  async getUserRoles() {
    const result = await this.pluginUserService.getUserRoles().toPromise();
    this.userRoles = result;
  }

  async add() {
    if (this.isBusy()) {
      return;
    }

    this.setPage(this.getLastPage());

    const newItem = this.pluginUserService.createNew();
    newItem.authenticationType = this.filter.authType;

    this.rows.push(newItem);
    super.count = this.count + 1;

    this.selected.length = 0;
    this.selected.push(newItem);

    this.setIsDirty();

    const ok = await this.openItemInEditForm(newItem);
    if (!ok) {
      this.rows.pop();
      super.count = this.count - 1;
      super.selected = [];
      this.setIsDirty();
    }
  }

  async edit(row?: PluginUserRO) {
    row = row || this.selected[0];
    const rowCopy = Object.assign({}, row);

    const ok = await this.openItemInEditForm(rowCopy);
    if (ok) {
      if (JSON.stringify(row) !== JSON.stringify(rowCopy)) { // the object changed
        Object.assign(row, rowCopy);
        if (row.status === UserState[UserState.PERSISTED]) {
          row.status = UserState[UserState.UPDATED];
          this.setIsDirty();
        }
      }
    }
  }

  private openItemInEditForm(item: PluginUserRO) {
    var editForm;
    if (this.inBasicMode()) {
      editForm = EditBasicPluginUserFormComponent;
    } else {
      editForm = EditCertificatePluginUserFormComponent;
    }

    return this.dialogsService.open(editForm, {
      data: {
        user: item,
        userroles: this.userRoles,
      }
    }).afterClosed().toPromise();
  }

  async doSave(): Promise<any> {
    try {
      if (this.inBasicMode()) {
        await this.pluginUserValidatorService.checkPluginUserNameDuplication(this.rows);
      } else {
        await this.pluginUserValidatorService.checkPluginUserCertificateDuplication(this.rows);
      }
      return this.pluginUserService.saveUsers(this.rows).then(() => this.filterData());
    } catch (ex) {
      this.alertService.exception('Cannot save users:', ex);
      return Promise.reject(ex);
    }
  }

  setIsDirty() {
    super.isChanged = this.rows.filter(el => el.status !== UserState[UserState.PERSISTED]).length > 0;
  }

  delete(row?: any) {
    const itemToDelete = row || this.selected[0];
    if (itemToDelete.status === UserState[UserState.NEW]) {
      this.rows.splice(this.rows.indexOf(itemToDelete), 1);
    } else {
      itemToDelete.status = UserState[UserState.REMOVED];
    }
    this.setIsDirty();
    this.selected.length = 0;
  }

  get csvUrl(): string {
    return PluginUserService.CSV_URL + '?' + this.createAndSetParameters();
  }

  protected readonly SelectionType = SelectionType;
}
