import {
  AfterViewChecked,
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  OnInit,
  TemplateRef,
  ViewChild
} from '@angular/core';
import {PartyService} from './support/party.service';
import {PartyFilteredResult, PartyResponseRo, ProcessRo} from './support/party';
import {AlertService} from '../common/alert/alert.service';
import {PartyDetailsComponent} from './party-details/party-details.component';
import {DirtyOperations} from '../common/dirty-operations';
import {CurrentPModeComponent} from '../pmode/current/currentPMode.component';
import {HttpClient} from '@angular/common/http';
import mix from '../common/mixins/mixin.utils';
import BaseListComponent from '../common/mixins/base-list.component';
import {ClientFilterableListMixin} from '../common/mixins/filterable-list.mixin';
import ModifiableListMixin from '../common/mixins/modifiable-list.mixin';
import {DialogsService} from '../common/dialogs/dialogs.service';
import {ClientPageableListMixin} from '../common/mixins/pageable-list.mixin';
import {ApplicationContextService} from '../common/application-context.service';
import {ComponentName} from '../common/component-name-decorator';
import {Server} from '../security/Server';

/**
 * @author Thomas Dussart, Ion Perpegel
 * @since 4.0
 */

@Component({
  selector: 'app-party',
  providers: [PartyService],
  templateUrl: './party.component.html',
  styleUrls: ['./party.component.css']
})
@ComponentName('Parties')
export class PartyComponent extends mix(BaseListComponent)
  .with(ClientFilterableListMixin, ModifiableListMixin, ClientPageableListMixin)
  implements OnInit, DirtyOperations, AfterViewInit, AfterViewChecked {

  @ViewChild('rowActions') rowActions: TemplateRef<any>;

  allRows: PartyResponseRo[];

  newParties: PartyResponseRo[];
  updatedParties: PartyResponseRo[];
  deletedParties: PartyResponseRo[];

  allProcesses: string[];

  pModeExists: boolean;

  constructor(private applicationService: ApplicationContextService, private dialogsService: DialogsService,
              public partyService: PartyService, public alertService: AlertService, private http: HttpClient,
              private changeDetector: ChangeDetectorRef) {
    super();
  }

  async ngOnInit() {
    super.ngOnInit();

    this.allRows = [];

    this.newParties = [];
    this.updatedParties = [];
    this.deletedParties = [];

    const res = await this.http.get<any>(CurrentPModeComponent.PMODE_URL + '/current').toPromise();
    if (res) {
      this.pModeExists = true;
      this.filterData();
    } else {
      this.pModeExists = false;
    }
  }

  ngAfterViewInit() {
    this.initColumns();
  }

  ngAfterViewChecked() {
    this.changeDetector.detectChanges();
  }

  isDirty(): boolean {
    return this.newParties.length + this.updatedParties.length + this.deletedParties.length > 0;
  }

  resetDirty() {
    this.newParties.length = 0;
    this.updatedParties.length = 0;
    this.deletedParties.length = 0;
  }

  async getDataAndSetResults(): Promise<any> {
    return this.partyService.getData(this.activeFilter).then(data => {
      const partiesRes: PartyFilteredResult = data[0];
      const processes: ProcessRo[] = data[1];

      this.allProcesses = processes.map(el => el.name);

      this.allRows = partiesRes.allData;
      super.rows = partiesRes.data;
      super.count = this.allRows.length;

      this.resetDirty();
    });
  }

  initColumns() {
    this.columnPicker.allColumns = [
      {
        name: 'Party Name',
        prop: 'name',
        width: 200,
        minWidth: 190,
      },
      {
        name: 'End Point',
        prop: 'endpoint',
        width: 450,
        minWidth: 440,
      },
      {
        name: 'Party Id',
        prop: 'joinedIdentifiers',
        width: 200,
        minWidth: 190,
      },
      {
        name: 'Process (I=Initiator, R=Responder, IR=Both)',
        prop: 'joinedProcesses',
        width: 350,
        minWidth: 340,
      },
      {
        cellTemplate: this.rowActions,
        name: 'Actions',
        prop: 'actions',
        width: 150,
        minWidth: 140,
        canAutoResize: true,
        sortable: false
      }
    ];
    this.columnPicker.selectedColumns = this.columnPicker.allColumns.filter(col => {
      return ['name', 'endpoint', 'joinedIdentifiers', 'joinedProcesses', 'actions']
        .indexOf(col.prop) !== -1
    })
  }

  get csvUrl(): string {
    return PartyService.CSV_PARTIES
      + this.partyService.getFilterPath(this.activeFilter.name, this.activeFilter.endPoint, this.activeFilter.partyID, this.activeFilter.process);
  }

  canAdd() {
    return !!this.pModeExists && super.canAdd();
  }

  canEdit() {
    return !!this.pModeExists && super.canEdit();
  }

  async doSave(): Promise<any> {
    try {
      await this.partyService.validateParties(this.allRows)
    } catch (err) {
      this.alertService.exception('Party validation error: <br>', err);
      return false;
    }

    return this.partyService.updateParties(this.allRows)
      .then((res) => {
        this.resetDirty();
        return res;
      });
  }

  async add() {
    if (this.isBusy()) {
      return;
    }

    this.setPage(this.getLastPage());

    const newParty = this.partyService.initParty();
    this.rows.push(newParty);
    this.allRows.push(newParty);

    this.selected.length = 0;
    this.selected.push(newParty);
    super.count++;

    this.newParties.push(newParty);
    const ok = await this.edit(newParty);
    if (!ok) {
      this.delete();
    }
    super.rows = [...this.rows];
  }

  delete() {
    if (this.isSaving) {
      return;
    }
    if (!this.selected || this.selected.length == 0) {
      return;
    }

    this.deleteRow(this.selected[0])
  }

  deleteRow(row) {
    if (!row) {
      return;
    }

    this.rows.splice(this.rows.indexOf(row), 1);
    this.allRows.splice(this.allRows.indexOf(row), 1);
    super.rows = [...this.rows];

    this.selected.length = 0;
    super.count--;

    if (this.newParties.indexOf(row) < 0) {
      this.deletedParties.push(row);
    } else {
      this.newParties.splice(this.newParties.indexOf(row), 1);
    }
  }

  async edit(row?): Promise<boolean> {
    row = row || this.selected[0];

    await this.manageCertificate(row);

    const edited = JSON.parse(JSON.stringify(row)); // clone
    const allProcessesCopy = JSON.parse(JSON.stringify(this.allProcesses));

    const dialogRef = this.dialogsService.open(PartyDetailsComponent, {
      data: {
        edit: edited,
        allProcesses: allProcessesCopy
      }
    });

    const ok = await dialogRef.afterClosed().toPromise();
    if (ok) {
      const rowCopy: PartyResponseRo = JSON.parse(JSON.stringify(row));
      // just for the sake of comparison
      rowCopy.processesWithPartyAsInitiator.forEach(el => el.entityId = 0);
      rowCopy.processesWithPartyAsResponder.forEach(el => el.entityId = 0);

      if (JSON.stringify(rowCopy) === JSON.stringify(edited)) {
        // nothing changed
        return;
      }

      Object.assign(row, edited);
      row.name = edited.name;
      super.rows = [...this.rows];

      if (this.updatedParties.indexOf(row) < 0) {
        this.updatedParties.push(row);
      }
    }

    return ok;
  }

  async manageCertificate(party: PartyResponseRo) {
    if (party.name && this.isPersisted(party) && party.certificate === undefined) {
      try {
        const cert = await this.partyService.getCertificate(party.name).toPromise();
        party.certificate = cert;
      } catch (ex) {
        if (this.isCertificateNotFound(ex)) {
          party.certificate = null;
        } else {
          this.alertService.exception(`Could not get the certificate for the party ${party.name}`, ex);
        }
      }
    }
  }

  private isCertificateNotFound(ex) {
    return ex.status == Server.HTTP_NOTFOUND;
  }

  private isPersisted(party: PartyResponseRo) {
    return party.entityId != null;
  }

  OnSort() {
    super.resetFilters();
  }

}
