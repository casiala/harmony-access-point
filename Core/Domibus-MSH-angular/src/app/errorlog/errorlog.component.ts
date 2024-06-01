﻿import {
  AfterViewChecked,
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  ElementRef,
  OnInit,
  TemplateRef,
  ViewChild
} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {ErrorLogResult} from './support/errorlogresult';
import {AlertService} from '../common/alert/alert.service';
import {ErrorlogDetailsComponent} from 'app/errorlog/errorlog-details/errorlog-details.component';
import {MatDialogRef} from '@angular/material/dialog';
import mix from '../common/mixins/mixin.utils';
import BaseListComponent from '../common/mixins/base-list.component';
import FilterableListMixin from '../common/mixins/filterable-list.mixin';
import {ServerSortableListMixin} from '../common/mixins/sortable-list.mixin';
import {ServerPageableListMixin} from '../common/mixins/pageable-list.mixin';
import {ApplicationContextService} from '../common/application-context.service';
import {ComponentName} from '../common/component-name-decorator';
import {Moment} from 'moment';
import {DialogsService} from '../common/dialogs/dialogs.service';
import {DateService} from '../common/customDate/date.service';

@Component({
  templateUrl: 'errorlog.component.html',
  providers: [],
  styleUrls: ['./errorlog.component.css']
})
@ComponentName('Error Logs')
export class ErrorLogComponent extends mix(BaseListComponent)
  .with(FilterableListMixin, ServerSortableListMixin, ServerPageableListMixin)
  implements OnInit, AfterViewInit, AfterViewChecked {

  static readonly ERROR_LOG_URL: string = 'rest/errorlogs';
  static readonly ERROR_LOG_CSV_URL: string = ErrorLogComponent.ERROR_LOG_URL + '/csv?';

  @ViewChild('rowWithDateFormatTpl') rowWithDateFormatTpl: TemplateRef<any>;
  @ViewChild('rawTextTpl') public rawTextTpl: TemplateRef<any>;

  timestampFromMaxDate: Date;
  timestampToMinDate: Date;
  timestampToMaxDate: Date;

  notifiedFromMaxDate: Date;
  notifiedToMinDate: Date;
  notifiedToMaxDate: Date;

  mshRoles: string[];
  errorCodes: string[];

  constructor(private applicationService: ApplicationContextService, private elementRef: ElementRef, private http: HttpClient,
              private alertService: AlertService, public dialogService: DialogsService, private changeDetector: ChangeDetectorRef,
              private dateService: DateService) {
    super();
  }

  ngOnInit() {
    super.ngOnInit();

    super.orderBy = 'timestamp';
    super.asc = false;

    this.setDateParams();

    this.filterData();
  }

  private setDateParams() {
    let todayEndDay = this.dateService.todayEndDay();

    this.timestampFromMaxDate = todayEndDay;
    this.timestampToMaxDate = todayEndDay;
    this.filter.timestampTo = todayEndDay;

    this.notifiedFromMaxDate = todayEndDay;
    this.notifiedToMaxDate = todayEndDay;

    this.timestampToMinDate = null;
    this.notifiedToMinDate = null;
  }

  ngAfterViewInit() {
    this.columnPicker.allColumns = [
      {
        name: 'Signal Message Id',
        prop: 'errorSignalMessageId',
        width: 200,
        minWidth: 190
      },
      {
        name: 'AP Role',
        prop: 'mshRole',
        width: 130,
        minWidth: 120
      },
      {
        name: 'Message Id',
        cellTemplate: this.rawTextTpl,
        prop: 'messageInErrorId',
        width: 350,
        minWidth: 340
      },
      {
        name: 'Error Code',
        width: 120,
        minWidth: 120
      },
      {
        name: 'Error Detail',
        cellTemplate: this.rawTextTpl,
        width: 400,
        minWidth: 390
      },
      {
        cellTemplate: this.rowWithDateFormatTpl,
        name: 'Timestamp',
        width: 200,
        minWidth: 190
      },
      {
        cellTemplate: this.rowWithDateFormatTpl,
        name: 'Notified',
        width: 200,
        minWidth: 190
      }

    ];

    this.columnPicker.selectedColumns = this.columnPicker.allColumns.filter(col => {
      return ['Message Id', 'Error Code', 'Timestamp'].indexOf(col.name) != -1
    });
  }

  ngAfterViewChecked() {
    this.changeDetector.detectChanges();
  }

  protected get GETUrl(): string {
    return ErrorLogComponent.ERROR_LOG_URL;
  }

  setServerResults(result: ErrorLogResult) {
    super.count = result.count;
    super.rows = result.errorLogEntries;

    if (result.filter.timestampFrom) {
      result.filter.timestampFrom = new Date(result.filter.timestampFrom);
    }
    if (result.filter.timestampTo) {
      result.filter.timestampTo = new Date(result.filter.timestampTo);
    }
    if (result.filter.notifiedFrom) {
      result.filter.notifiedFrom = new Date(result.filter.notifiedFrom);
    }
    if (result.filter.notifiedTo) {
      result.filter.notifiedTo = new Date(result.filter.notifiedTo);
    }

    super.filter = result.filter;
    this.mshRoles = result.mshRoles;
    this.errorCodes = result.errorCodes;
  }

  onTimestampFromChange(param: Moment) {
    if (param) {
      this.timestampToMinDate = param.toDate();
      this.filter.timestampFrom = param.toDate();
    } else {
      this.timestampToMinDate = null;
      this.filter.timestampFrom = null;
    }
  }

  onTimestampToChange(param: Moment) {
    if (param) {
      let date = param.toDate();
      this.dateService.setEndDay(date);
      this.timestampFromMaxDate = date;
      this.filter.timestampTo = date;
    } else {
      this.filter.timestampTo = null;
      this.timestampFromMaxDate = this.dateService.todayEndDay();
    }
  }

  onNotifiedFromChange(param: Moment) {
    if (param) {
      this.notifiedToMinDate = param.toDate();
      this.filter.notifiedFrom = param.toDate();
    } else {
      this.notifiedToMinDate = null;
      this.filter.notifiedFrom = null;
    }
  }

  onNotifiedToChange(param: Moment) {
    if (param) {
      let date = param.toDate();
      this.dateService.setEndDay(date);
      this.notifiedFromMaxDate = date;
      this.filter.notifiedTo = date;
    } else {
      this.notifiedFromMaxDate = this.dateService.todayEndDay();
      this.filter.notifiedTo = null;
    }
  }

  showDetails(selectedRow: any) {
    let dialogRef: MatDialogRef<ErrorlogDetailsComponent> = this.dialogService.open(ErrorlogDetailsComponent, {autoFocus: false});
    dialogRef.componentInstance.message = selectedRow;
  }

  get csvUrl(): string {
    return ErrorLogComponent.ERROR_LOG_CSV_URL + this.createAndSetParameters();
  }
}
