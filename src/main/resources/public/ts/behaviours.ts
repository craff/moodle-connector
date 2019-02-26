import {Behaviours} from "entcore";

console.log('moodle behaviours loaded');

Behaviours.register('moodle', {
	rights: {
		workflow: {
			view: 'fr.openent.moodle.controllers.MoodleController|view',
			delete: 'fr.openent.moodle.controllers.MoodleController|delete',
			create: 'fr.openent.moodle.controllers.MoodleController|create'
		},
		resource: {
			manager: {
				right: 'fr-openent-moodle-controllers-MoodleController|create'
			},
			contrib: {
				right: 'fr-openent-moodle-controllers-MoodleController|delete',
			},
			read: {
				right: 'fr-openent-moodle-controllers-MoodleController|view'
			}
		}
	},
	dependencies: {},

	loadResources: function (callback) { }

	/*loadResources: async function(callback){
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
	}*/
});
