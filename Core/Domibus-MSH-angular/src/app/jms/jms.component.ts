import {
  AfterViewChecked,
  AfterViewInit,
  ChangeDetectorRef,
  Component,
  EventEmitter,
  OnInit,
  TemplateRef,
  ViewChild
} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {AlertService} from '../common/alert/alert.service';
import {MessagesRequestRO} from './ro/messages-request-ro';
import {MatDialogRef} from '@angular/material/dialog';
import {MoveDialogComponent} from './move-dialog/move-dialog.component';
import {MessageDialogComponent} from './message-dialog/message-dialog.component';
import {DirtyOperations} from '../common/dirty-operations';
import {Observable} from 'rxjs/Observable';
import mix from '../common/mixins/mixin.utils';
import BaseListComponent from '../common/mixins/base-list.component';
import FilterableListMixin from '../common/mixins/filterable-list.mixin';
import {DialogsService} from '../common/dialogs/dialogs.service';
import ModifiableListMixin from '../common/mixins/modifiable-list.mixin';
import {ClientPageableListMixin} from '../common/mixins/pageable-list.mixin';
import {ClientSortableListMixin} from '../common/mixins/sortable-list.mixin';
import {ApplicationContextService} from '../common/application-context.service';
import {ComponentName} from '../common/component-name-decorator';
import {Moment} from 'moment/moment';
import {DateService} from '../common/customDate/date.service';

@Component({
  selector: 'app-jms',
  templateUrl: './jms.component.html',
  styleUrls: ['./jms.component.css']
})
@ComponentName('JMS Messages')
export class JmsComponent extends mix(BaseListComponent)
  .with(FilterableListMixin, ClientPageableListMixin, ModifiableListMixin, ClientSortableListMixin)
  implements OnInit, DirtyOperations, AfterViewInit, AfterViewChecked {

  private readonly _dlqName = '.*?[d|D]omibus.?DLQ';
  private readonly queueNamePrefixSeparator = '@';
  private readonly originalQueuePrefixSeparator = '!';

  timestampFromMaxDate: Date;
  timestampToMinDate: Date;
  timestampToMaxDate: Date;

  defaultQueueSet: EventEmitter<boolean>;
  queuesInfoGot: EventEmitter<boolean>;

  @ViewChild('rowWithDateFormatTpl') rowWithDateFormatTpl: TemplateRef<Object>;
  @ViewChild('rowActions') rowActions: TemplateRef<any>;
  @ViewChild('rawTextTpl') public rawTextTpl: TemplateRef<any>;

  queues: any[];
  filteredQueues: any[];
  originalQueues: any[];

  currentSearchSelectedSource;

  markedForDeletionMessages: any[];

  request: MessagesRequestRO;

  private _selectedSource: any;
  private originalQueuePrefix: string;
  originalQueueName: string;

  get selectedSource(): any {
    return this._selectedSource;
  }

  set selectedSource(value: any) {
    const oldVal = this._selectedSource;
    this._selectedSource = value;
    this.filter.source = value.name;
    this.defaultQueueSet.emit(oldVal);
    this.originalQueueName = null;
    this.filter.originalQueue = null;
  }

  constructor(private applicationService: ApplicationContextService, private http: HttpClient, private alertService: AlertService,
              private dialogsService: DialogsService, private changeDetector: ChangeDetectorRef, private dateService: DateService) {
    super();
  }

  ngOnInit() {
    super.ngOnInit();

    super.filter = new MessagesRequestRO();

    this.timestampFromMaxDate = new Date();
    this.timestampToMinDate = null;
    this.timestampToMaxDate = new Date();

    this.defaultQueueSet = new EventEmitter(false);
    this.queuesInfoGot = new EventEmitter(false);

    this.queues = [];
    this.filteredQueues = [];
    this.originalQueues = [];

    this.originalQueueName = null;

    this.markedForDeletionMessages = [];

    this.setDateParams();

    this.loadDestinations();

    this.queuesInfoGot.subscribe(result => {
      this.setDefaultQueue(this._dlqName);
    });

    this.defaultQueueSet.subscribe(oldVal => {
      super.tryFilter(false).then(done => {
        if (!done) {
          // revert the drop-down value to the old one
          this._selectedSource = oldVal;
        }
      });
    });
  }

  private setDateParams() {
    let todayEndDay = this.dateService.todayEndDay();

    this.filter.toDate = todayEndDay;

    this.timestampFromMaxDate = todayEndDay;
    this.timestampToMaxDate = todayEndDay;

    this.timestampToMinDate = null;
  }

  public async tryFilter(userInitiated = true): Promise<boolean> {
    console.log('filter=', this.filter);
    return super.tryFilter(userInitiated);
  }

  ngAfterViewInit() {
    this.columnPicker.allColumns = [
      {
        name: 'ID',
        prop: 'id'
      },
      {
        name: 'JMS Type',
        prop: 'type',
        width: 80
      },
      {
        cellTemplate: this.rowWithDateFormatTpl,
        name: 'Time',
        prop: 'timestamp',
        width: 50
      },
      {
        name: 'Custom prop',
        prop: 'customPropertiesText',
        width: 400
      },
      {
        name: 'JMS prop',
        prop: 'jmspropertiesText',
        width: 250
      },
      {
        cellTemplate: this.rowActions,
        name: 'Actions',
        width: 10,
        sortable: false
      }

    ];

    this.columnPicker.selectedColumns = this.columnPicker.allColumns.filter(col => {
      return ['ID', 'Time', 'Custom prop', 'JMS prop', 'Actions'].indexOf(col.name) != -1
    });
  }

  ngAfterViewChecked() {
    this.changeDetector.detectChanges();
  }

  protected onSetFilters() {
    this._selectedSource = this.queues.find(el => el.name == this.filter.source);
  }

  private getDestinations(): Observable<any> {
    return this.http.get<any>('rest/jms/destinations')
      .map(response => response.jmsDestinations)
      .catch((error) => this.alertService.exception('Could not load queues ', error));
  }

  private loadDestinations() {
    this.getDestinations().subscribe(
      (destinations) => {
        this.queues = [];
        for (const key in destinations) {
          this.queues.push(destinations[key]);
        }
        this.filteredQueues = this.queues;
        this.originalQueues = this.queues;
        this.queuesInfoGot.emit();
      }
    );
  }

  private refreshDestinations(): Observable<any> {
    const result = this.getDestinations();
    result.subscribe(
      (destinations) => {
        for (const key in destinations) {
          const src = destinations[key];
          const queue = this.queues.find(el => el.name === src.name);
          if (queue) {
            Object.assign(queue, src);
          }
        }
      }
    );
    return result;
  }

  private setDefaultQueue(queueName: string) {
    if (!this.queues || this.queues.length == 0) {
      return;
    }

    const matching = this.queues.find((el => el.name && el.name.match(queueName)));
    const toSelect = matching != null ? matching : this.queues.length[0];

    this.selectedSource = toSelect;
  }

  edit(row) {
    this.showDetails(row);
  }

  onTimestampFromChange(param: Moment) {
    if (param) {
      this.timestampToMinDate = param.toDate();
      this.filter.fromDate = param.toDate();
    } else {
      this.timestampToMinDate = null;
      this.filter.fromDate = null;
    }
  }

  onTimestampToChange(param: Moment) {
    if (param) {
      let date = param.toDate();
      this.dateService.setEndDay(date);
      this.timestampFromMaxDate = date;
      this.filter.toDate = date;
    } else {
      this.timestampFromMaxDate = this.dateService.todayEndDay();
      this.filter.toDate = null;
    }
  }

  canSearch() {
    return this.filter.source && super.canSearch();
  }

  protected get GETUrl(): string {
    return 'rest/jms/messages';
  }

  protected async onBeforeGetData(): Promise<any> {
    if (!this.filter.source) {
      return Promise.reject('Source should be set');
    }

    this.markedForDeletionMessages = [];
    this.currentSearchSelectedSource = this.selectedSource;
  }

  protected onLoadDataError(error) {
    this.alertService.error('An error occurred while loading the JMS messages. In case you are using the Selector / JMS Type, please follow the rules for Selector / JMS Type according to Help Page / Admin Guide');
    console.log('Error: ', error.status, error.error);
    error.handled = true;
  }

  public setServerResults(res) {
    const rows: any[] = res.messages;
    rows.forEach(row => {
      row.customPropertiesText = JSON.stringify(row.customProperties);
      row.jmspropertiesText = JSON.stringify(row.jmsproperties);
    });
    super.rows = rows;
    super.count = rows.length;
    this.refreshDestinations();
  }

  async doSave(): Promise<void> {
    const messageIds = this.markedForDeletionMessages.map((message) => message.id);
    // because the user can change the source after pressing search and then select the messages and press delete
    // in this case I need to use currentSearchSelectedSource
    try {
      await this.serverRemove(this.currentSearchSelectedSource.name, messageIds);
    } catch (ex) {
      throw new Error('Exception trying to delete messages:' + this.alertService.tryExtractErrorMessageFromResponse(ex));
    }
  }

  moveElements(elements: any[]) {
    if (!elements || elements.length == 0) {
      return;
    }

    // a jms message can be moved only from DLQ
    if (/DLQ/.test(this.currentSearchSelectedSource.name) == false) {
      this.alertService.error('Moving messages is only allowed from DLQ queue');
      return;
    }

    try {
      let queues = this.getAllowedDestinationQueues(elements);
      this.dialogsService.open(MoveDialogComponent, {data: {queues: queues}})
        .afterClosed().subscribe(result => {
        if (result && result.destination) {
          const messageIds = elements.map((message) => message.id);
          let payload = {
            source: this.currentSearchSelectedSource.name,
            destination: result.destination,
            selectedMessages: messageIds,
            action: 'MOVE'
          };
          this.serverMoveSelected(payload);
        }
      });
    } catch (ex) {
      this.alertService.exception('Exception trying to move messages:', ex);
    }
  }

  private getAllowedDestinationQueues(messages: any[]) {
    let originalQueueName: any;
    if (messages.length > 1) {
      originalQueueName = this.getCommonOriginalQueueName(messages);
    } else {
      const message = messages[0];
      originalQueueName = this.getOriginalQueueName(message);
    }
    console.log(`Original queue name for the message: [${originalQueueName}]. Current queue name: [${this.selectedSource.name}]. Current queue details: `, this.selectedSource);

    let allowedQueues: any[];
    if (originalQueueName) {
      // a jms message with originalQueue property can be moved only to the original queue
      allowedQueues = this.queues.filter(queue => this.isMatch(queue, originalQueueName));
      if (allowedQueues.length == 0) {
        throw new Error(`Cannot move the selected messages because the original queue [${originalQueueName}] cannot be found.`);
      }
    } else {
      // a jms message without originalQueue property can be moved in any queue
      console.warn(`Could not find the original queue [${originalQueueName}] for the selected message; returning all as allowed destination queues.`);
      allowedQueues = this.queues;
    }

    // exclude current source queue
    console.log(`Excluding the current queue [${this.selectedSource.name}] from the allowed destination queues.`);
    allowedQueues = allowedQueues.filter(el => el.name != this.selectedSource.name);
    if (allowedQueues.length == 0) {
      throw new Error(`Cannot move the selected messages because the original queue [${originalQueueName}] is the same as the current queue.`);
    }
    return allowedQueues;
  }

  private isMatch(queue: any, name: string): boolean {
    return queue.name.includes(name) || name.includes(queue.name);
  }

  getCommonOriginalQueueName(messages: any[]): any {
    let originaleQueueNames = messages.map(msg => this.getOriginalQueueName(msg))
      .filter((msg, index, list) => list.indexOf(msg) === index);

    if (originaleQueueNames.length > 1) {
      throw new Error('Cannot move the messages because they have different original/destination queues.');
    }
    if (originaleQueueNames.length == 1) {
      return originaleQueueNames[0];
    }
    return null;
  }

  getOriginalQueueName(message): any {
    let originalQueueName = message.customProperties.originalQueue;
    if (!originalQueueName) {
      return null;
    }
    // EDELIVERY-2814
    originalQueueName = originalQueueName.substr(originalQueueName.indexOf(this.originalQueuePrefixSeparator) + 1);
    return originalQueueName;
  }

  moveSelected() {
    this.moveElements(this.selected);
  }

  moveAction(row) {
    this.moveElements([row]);
  }

  showDetails(selectedRow: any) {
    const dialogRef: MatDialogRef<MessageDialogComponent> = this.dialogsService.open(MessageDialogComponent);
    dialogRef.componentInstance.message = selectedRow;
    dialogRef.componentInstance.currentSearchSelectedSource = this.currentSearchSelectedSource;
  }

  deleteAction(row) {
    this.deleteElements([row]);
  }

  delete() {
    this.deleteElements(this.selected);
  }

  async deleteAll() {
    const yes = await this.confirmDeleteAllDialog();
    if (yes) {
      this.serverRemoveAll(this.currentSearchSelectedSource.name);
    }
  }

  private confirmDeleteAllDialog(): Promise<boolean> {
    return this.dialogsService.openYesNoDialogDialog({
      data: {
        title: 'Are you sure you want to delete all messages?'
      }
    });
  }

  deleteElements(elements: any[]) {
    elements.forEach(el => {
      const index = this.rows.indexOf(el);
      if (index > -1) {
        this.rows.splice(index, 1);
        this.markedForDeletionMessages.push(el);
      }
    });
    super.rows = [...this.rows];
    super.count = this.rows.length;

    super.selected = [];
  }

  async serverMoveSelected(payload: MessagesRequestRO) {
    super.isSaving = true;
    try {
      await this.http.post('rest/jms/messages/action', payload).toPromise();
      // refresh destinations
      this.refreshDestinations().subscribe(res => {
        this.setDefaultQueue(this.currentSearchSelectedSource.name);
      });

      // remove the selected rows
      this.deleteElements(this.selected);
      this.markedForDeletionMessages = [];

      this.alertService.success('The operation \'move messages\' completed successfully.');
    } catch (error) {
      this.alertService.exception('The operation \'move messages\' could not be completed: ', error);
    } finally {
      super.isSaving = false;
    }
  }

  async serverRemove(source: string, messageIds: Array<any>): Promise<void> {
    try {
      const res = await this.http.post('rest/jms/messages/action', {
        source: source,
        selectedMessages: messageIds,
        action: 'REMOVE'
      }).toPromise();
      this.refreshDestinations();
      this.markedForDeletionMessages = [];
    } catch (ex) {
      throw ex;
    }
  }

  async serverRemoveAll(source: string): Promise<any> {
    try {
      const messageIds = this.rows.map(el => el.id);
      await this.serverRemove(source, messageIds);
      super.rows = [];
    } catch (ex) {
      this.alertService.exception('Exception trying to delete filtered messages:', ex);
      return null
    }
  }

  saveAsCSV() {
    if (!this.activeFilter.source) {
      this.alertService.error('Source should be set');
      return;
    }

    super.saveAsCSV();
  }

  get csvUrl(): string {
    return 'rest/jms/csv?' + this.createAndSetParameters();
  }

  isDirty(): boolean {
    return this.markedForDeletionMessages && this.markedForDeletionMessages.length > 0;
  }

  canCancel() {
    return this.isDirty() && !this.isBusy();
  }

  canSave() {
    return this.canCancel();
  }

  canDelete() {
    return this.atLeastOneRowSelected() && !this.isBusy();
  }

  canMove() {
    return this.canDelete();
  }

  canDeleteAll() {
    return this.rows.length > 0;
  }

  private atLeastOneRowSelected() {
    return this.selected.length > 0;
  }

  moveAll() {
    let payload: MessagesRequestRO = {
      source: this.currentSearchSelectedSource.name,
      destination: this.originalQueueName,
      originalQueue: this.getOriginalQueueForFiltering(),
      action: 'MOVE_ALL',
      jmsType: this.filter.jmsType,
      fromDate: this.filter.fromDate,
      toDate: this.filter.toDate,
      selector: this.filter.selector
    };
    this.serverMoveSelected(payload);
  }

  canMoveAll() {
    return !this.isBusy()
      && this.isDLQQueue()
      && this.rows.length
      && this.filter.originalQueue
      && this.isFiltered();
  }

  isDLQQueue() {
    return this.selectedSource && this.selectedSource.name.match(this._dlqName);
  }

  onSelectOriginalQueue() {
    this.calculateOriginalQueuePrefix();

    this.filter.originalQueue = this.getOriginalQueueForFiltering();
  }

  getOriginalQueueForFiltering() {
    let originalQueue = this.originalQueueName;
    if (originalQueue.indexOf(this.queueNamePrefixSeparator) < 0) {
      return originalQueue;
    }

    // cluster mode: need to prefix
    let destination = originalQueue.substr(originalQueue.indexOf(this.queueNamePrefixSeparator) + 1);
    return this.originalQueuePrefix + destination;
  }

  calculateOriginalQueuePrefix() {
    if (!this.rows.length) {
      return;
    }

    const message = this.rows[0];
    let originalQueueName = message.customProperties.originalQueue;
    if (!originalQueueName) {
      return;
    }

    this.originalQueuePrefix = originalQueueName.substr(0, originalQueueName.indexOf(this.originalQueuePrefixSeparator) + 1);
  }


  onFilterSourceQueuesKey(value: any) {
    this.filteredQueues = this.queues.filter(queue => queue.name && queue.name.toLowerCase().includes(value));
  }

  onFilterOriginalQueuesKey(value: any) {
    this.originalQueues = this.queues.filter(queue => queue.name && queue.name.toLowerCase().includes(value));
  }
}
