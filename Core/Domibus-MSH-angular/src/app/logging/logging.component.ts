import {AfterViewChecked, AfterViewInit, ChangeDetectorRef, Component, ElementRef, OnInit, TemplateRef, ViewChild} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {LoggingLevelResult} from './support/logginglevelresult';
import {AlertService} from '../common/alert/alert.service';
import mix from '../common/mixins/mixin.utils';
import BaseListComponent from '../common/mixins/base-list.component';
import FilterableListMixin from '../common/mixins/filterable-list.mixin';
import {ServerPageableListMixin} from '../common/mixins/pageable-list.mixin';
import {ApplicationContextService} from '../common/application-context.service';
import {ComponentName} from '../common/component-name-decorator';

/**
 * @author Catalin Enache
 * @since 4.1
 */
@Component({
  templateUrl: 'logging.component.html',
  providers: [],
})
@ComponentName('Logging')
export class LoggingComponent extends mix(BaseListComponent)
  .with(FilterableListMixin, ServerPageableListMixin)
  implements OnInit, AfterViewInit, AfterViewChecked {

  static readonly LOGGING_URL: string = 'rest/logging/loglevel';
  static readonly RESET_LOGGING_URL: string = 'rest/logging/reset';

  @ViewChild('rowWithToggleTpl') rowWithToggleTpl: TemplateRef<any>;

  levels: Array<String>;

  constructor(private applicationService: ApplicationContextService, private elementRef: ElementRef, private http: HttpClient,
              private alertService: AlertService, private changeDetector: ChangeDetectorRef) {
    super();
  }

  ngOnInit() {

    super.ngOnInit()
    this.filterData();
  }

  ngAfterViewInit() {
    this.columnPicker.allColumns = [
      {
        name: 'Logger Name',
        prop: 'name',
        sortable: false
      },
      {
        cellTemplate: this.rowWithToggleTpl,
        name: 'Logger Level',
        sortable: false
      }
    ];
    this.columnPicker.selectedColumns = this.columnPicker.allColumns.filter(col => {
      return ['Logger Name', 'Logger Level'].indexOf(col.name) != -1
    });
  }

  ngAfterViewChecked() {
    this.changeDetector.detectChanges();
  }

  protected get GETUrl(): string {
    return LoggingComponent.LOGGING_URL;
  }

  public setServerResults(result: LoggingLevelResult) {
    super.count = result.count;
    super.rows = result.loggingEntries;

    super.filter = result.filter;
    this.levels = result.levels;
  }

  onLevelChange(newLevel: string, row: any) {
    if (newLevel !== row.level) {
      this.alertService.clearAlert();
      this.http.post(LoggingComponent.LOGGING_URL, {
        name: row.name,
        level: newLevel,
      }, {headers: this.headers}).subscribe(
        () => {
          this.tryFilter();
        },
        error => {
          this.alertService.exception('An error occurred while setting logging level: ', error);
          super.isLoading = false;
        }
      );
    }
  }

  resetLogging() {
    this.http.post(LoggingComponent.RESET_LOGGING_URL, {}).subscribe(
      res => {
        this.alertService.success('Logging configuration was successfully reset.');
        this.page();
      },
      error => {
        this.alertService.exception('An error occurred while resetting logging: ', error);
        super.isLoading = false;
      }
    );
  }

}
