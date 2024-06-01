import {NgModule} from "@angular/core";

import {ClickStopPropagationDirective} from 'app/common/directive/attribute/ClickStopPropagation';
import {ButtonClickBehaviourDirective} from '../directive/ButtonClickBehaviour';
import {AdvancedFilterBehaviourDirective} from '../directive/AdvancedFilterBehaviour';
import {InputDebounceBehaviourDirective} from '../directive/InputDebounceBehaviour';
import {TriStateCheckboxComponent} from '../directive/tri-state-checkbox/tri-state-checkbox.component';
import {MatCheckboxModule} from '@angular/material/checkbox';
import {FormsModule} from '@angular/forms';
import {MatDialogModule} from '@angular/material/dialog';
import {MatIconModule} from '@angular/material/icon';
import {NgxMatDatetimePickerModule} from '@angular-material-components/datetime-picker';

@NgModule({
  declarations: [
    ClickStopPropagationDirective,
    ButtonClickBehaviourDirective,
    AdvancedFilterBehaviourDirective,
    InputDebounceBehaviourDirective,
    TriStateCheckboxComponent,
  ],
  imports: [
    NgxMatDatetimePickerModule,
    MatCheckboxModule,
    FormsModule,
    MatDialogModule,
    MatIconModule
  ],
  exports: [
    ClickStopPropagationDirective,
    ButtonClickBehaviourDirective,
    AdvancedFilterBehaviourDirective,
    InputDebounceBehaviourDirective,
    TriStateCheckboxComponent,
  ]
})
export class SharedModule {
}
