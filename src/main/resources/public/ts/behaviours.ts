import { Behaviours } from 'entcore';
import http from 'axios';

Behaviours.register('moodle', {
	rights: {
		workflow: {
			view: 'fr.openent.moodle.controllers.MoodleController|view',
			create: 'fr.openent.moodle.controllers.MoodleController|createMoodle'
		},
		resource: {
			read: "fr-openent-moodle-controllers-MoodleController|getMoodle",
			contrib: "fr-openent-moodle-controllers-MoodleController|updateMoodle",
			manager: "fr-openent-moodle-controllers-MoodleController|addRights"
		}
	},
	loadResources: async function(callback){
		const response = await http.get('/moodle/list');
		this.resources = response.data.filter(e => e.trashed === 0).map((moodle) => {
			moodle.icon = moodle.icon || '/img/illustrations/moodle-default.png';
			return {
				title: moodle.title,
				owner: moodle.owner,
				icon: moodle.icon,
				path: '/moodle#/view-moodle/' + moodle._id,
				_id: moodle._id
			};
		});
		return this.resources;
	}
});
