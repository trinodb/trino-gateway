import {api} from "../base";
import {RoutingRulesData} from "../../types/routing-rules";

export async function routingRulesApi(): Promise<any> {
        const response = await api.get('/webapp/getRoutingRules');
        return response;
}

export async function updateRoutingRulesApi(body: Record<string, any>): Promise<RoutingRulesData> {
    return api.post('/webapp/updateRoutingRules', body)
}
