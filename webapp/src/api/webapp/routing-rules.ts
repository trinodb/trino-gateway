import {api} from "../base";
import {RoutingRulesData} from "../../types/routing-rules";

export async function routingRulesApi(): Promise<any> {
        const response = await api.get('/webapp/getRoutingRules');
        return response;
}

export async function updateRoutingRulesApi(body: Record<string, any>): Promise<RoutingRulesData> {
    return api.post('/webapp/updateRoutingRules', body)
}

export async function createRoutingRuleApi(body: RoutingRulesData): Promise<RoutingRulesData[]> {
    return api.post('/webapp/createRoutingRule', body)
}

export async function deleteRoutingRuleApi(name: string): Promise<RoutingRulesData[]> {
    return api.post('/webapp/deleteRoutingRule', {name})
}
