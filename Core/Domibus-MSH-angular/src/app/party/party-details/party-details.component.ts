import {AfterViewInit, ChangeDetectorRef, Component, Inject, OnInit, ViewChild} from '@angular/core';
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from '@angular/material/dialog';
import {ColumnPickerBase} from 'app/common/column-picker/column-picker-base';
import {IdentifierRo, PartyResponseRo, ProcessInfoRo} from '../support/party';
import {PartyIdentifierDetailsComponent} from '../party-identifier-details/party-identifier-details.component';
import {PartyService} from '../support/party.service';
import {AlertService} from '../../common/alert/alert.service';
import {EditPopupBaseComponent} from '../../common/edit-popup-base.component';
import {UntypedFormGroup} from '@angular/forms';
import {DialogsService} from '../../common/dialogs/dialogs.service';

@Component({
  selector: 'app-party-details',
  providers: [PartyService],
  templateUrl: './party-details.component.html',
  styleUrls: ['./party-details.component.css']
})
export class PartyDetailsComponent extends EditPopupBaseComponent implements OnInit, AfterViewInit {

  processesRows: ProcessInfoRo[] = [];
  allProcesses: string[];

  identifiersRowColumnPicker: ColumnPickerBase = new ColumnPickerBase();
  processesRowColumnPicker: ColumnPickerBase = new ColumnPickerBase();

  party: PartyResponseRo;
  identifiers: Array<IdentifierRo>;
  selectedIdentifiers = [];

  @ViewChild('fileInput')
  private fileInput;

  endpointPattern = '^(?:(?:(?:https?):)?\\/\\/)(?:\\S+)$';

  constructor(public dialogRef: MatDialogRef<PartyDetailsComponent>, @Inject(MAT_DIALOG_DATA) public data: any,
              private dialogsService: DialogsService, public partyService: PartyService, public alertService: AlertService,
              private cdr: ChangeDetectorRef) {

    super(dialogRef, data);

    this.party = data.edit;
    this.identifiers = this.party.identifiers;
    this.allProcesses = data.allProcesses;

    this.formatProcesses();
  }

  ngOnInit() {
    this.initColumns();
  }

  ngAfterViewInit() {
    this.cdr.detectChanges();
  }

  // transform processes to view-model
  private formatProcesses() {
    const processesWithPartyAsInitiator = this.party.processesWithPartyAsInitiator.map(el => el.name);
    const processesWithPartyAsResponder = this.party.processesWithPartyAsResponder.map(el => el.name);
    for (const proc of this.allProcesses) {
      const row = new ProcessInfoRo();
      row.name = proc;
      if (processesWithPartyAsInitiator.indexOf(proc) >= 0) {
        row.isInitiator = true;
      }
      if (processesWithPartyAsResponder.indexOf(proc) >= 0) {
        row.isResponder = true;
      }

      this.processesRows.push(row);
    }

    this.processesRows.sort((a, b) => {
        if (!!a.isInitiator > !!b.isInitiator) {
          return -1;
        }
        if (!!a.isInitiator < !!b.isInitiator) {
          return 1;
        }
        if (!!a.isResponder > !!b.isResponder) {
          return -1;
        }
        if (!!a.isResponder < !!b.isResponder) {
          return 1;
        }
        if (a.name < b.name) {
          return -1;
        }
        if (a.name > b.name) {
          return 1;
        }
        return 0;
      }
    );
  }

  uploadCertificate() {
    if (!this.party.name) {
      this.alertService.error('Please provide a party name in order to import a certificate.');
      return;
    }

    const fi = this.fileInput.nativeElement;
    const file = fi.files[0];

    const reader = new FileReader();
    reader.onload = (e) => {
      let binaryData = <string>reader.result;

      this.party.certificateContent = btoa(binaryData); // base64

      this.partyService.uploadCertificate({content: this.party.certificateContent}, this.party.name)
        .subscribe(res => {
            this.party.certificate = res;
            this.markDirty();
          },
          err => {
            this.alertService.exception('Error uploading certificate file ' + file.name, err);
          }
        );
    };
    reader.onerror = (err) => {
      this.alertService.exception('Error reding certificate file ' + file.name, err);
    };

    reader.readAsBinaryString(file);
  }

  initColumns() {
    this.identifiersRowColumnPicker.allColumns = [
      {
        name: 'Party Id',
        prop: 'partyId',
        width: 100
      },
      {
        name: 'Party Id Type',
        prop: 'partyIdType.name',
        width: 150
      },
      {
        name: 'Party Id Value',
        prop: 'partyIdType.value',
        width: 280
      }
    ];
    this.identifiersRowColumnPicker.selectedColumns = this.identifiersRowColumnPicker.allColumns.filter(col => {
      return ['Party Id', 'Party Id Type', 'Party Id Value'].indexOf(col.name) != -1
    });
  }

  async editIdentifier(): Promise<boolean> {
    const identifierRow = this.selectedIdentifiers[0];
    if (!identifierRow) {
      return;
    }

    const rowClone = JSON.parse(JSON.stringify(identifierRow));

    const dialogRef = this.dialogsService.open(PartyIdentifierDetailsComponent, {
      data: {
        edit: rowClone
      }
    });

    const ok = await dialogRef.afterClosed().toPromise();
    if (ok) {
      if (JSON.stringify(identifierRow) !== JSON.stringify(rowClone)) {
        Object.assign(identifierRow, rowClone);
        this.markDirty();
      }
    }

    return ok;
  }

  private markDirty() {
    if (!(this.editForm instanceof UntypedFormGroup)) {
      this.editForm.form.markAsDirty();
    }
  }

  removeIdentifier() {
    const identifierRow = this.selectedIdentifiers[0];
    if (!identifierRow) {
      return;
    }

    this.party.identifiers.splice(this.party.identifiers.indexOf(identifierRow), 1);
    this.selectedIdentifiers.length = 0;
    this.markDirty();
  }

  async addIdentifier() {
    const identifierRow = {entityId: 0, partyId: '', partyIdType: {name: '', value: ''}};

    this.party.identifiers.push(identifierRow);

    this.selectedIdentifiers.length = 0;
    this.selectedIdentifiers.push(identifierRow);

    const ok = await this.editIdentifier();
    if (!ok) {
      this.removeIdentifier();
      this.markDirty();
    }
    this.party.identifiers = [...this.party.identifiers];
  }

  onSubmitForm() {
    this.persistProcesses();
    this.party.joinedIdentifiers = this.party.identifiers.map(el => el.partyId).join(', ');
  }

  persistProcesses() {
    this.party.processesWithPartyAsInitiator = [];
    this.party.processesWithPartyAsResponder = [];
    const rowsToProcess = this.processesRows.filter(el => el.isResponder || el.isInitiator);

    for (const proc of rowsToProcess) {
      if (proc.isInitiator) {
        this.party.processesWithPartyAsInitiator.push({entityId: 0, name: proc.name})
      }
      if (proc.isResponder) {
        this.party.processesWithPartyAsResponder.push({entityId: 0, name: proc.name})
      }
    }

    // set the string column too
    const initiatorElements = rowsToProcess.filter(el => el.isInitiator && !el.isResponder).map(el => el.name);
    const responderElements = rowsToProcess.filter(el => el.isResponder && !el.isInitiator).map(el => el.name);
    const bothElements = rowsToProcess.filter(el => el.isInitiator && el.isResponder).map(el => el.name);

    this.party.joinedProcesses = ((initiatorElements.length > 0) ? initiatorElements.join('(I), ') + '(I), ' : '')
      + ((responderElements.length > 0) ? responderElements.join('(R), ') + '(R), ' : '')
      + ((bothElements.length > 0) ? bothElements.join('(IR), ') + '(IR)' : '');

    if (this.party.joinedProcesses.endsWith(', ')) {
      this.party.joinedProcesses = this.party.joinedProcesses.substr(0, this.party.joinedProcesses.length - 2);
    }
  }

  onActivate(event) {
    if ('dblclick' === event.type) {
      this.editIdentifier();
    }
  }

  isFormDisabled() {
    return super.isFormDisabled() || this.party.identifiers.length == 0;
  }

  checkInitiator(row: any) {
    row.isInitiator = !row.isInitiator;
    this.markDirty();
  }

  checkResponder(row: any) {
    row.isResponder = !row.isResponder;
    this.markDirty();
  }

  onImportClicked($event: MouseEvent) {
    if (!this.party.name) {
      $event.preventDefault();
    }
  }
}
